package org.enso

import akka.NotUsed
import org.enso.flexer.Reader
import spray.json._

import scala.io.StdIn
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods._
import org.enso.syntax.text.AST
import org.enso.syntax.text.Parser

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.util.Try

case class ParserServerConfig(
  interface: String = ParserServerConfig.DEFAULT_HOSTNAME,
  port: Int         = ParserServerConfig.DEFAULT_PORT
) {
  def address_string(): String = s"ws://$interface:$port"
}

object ParserServerConfig {
  val HOSTNAME_VAR = "ENSO_PARSER_HOSTNAME"
  val PORT_VAR     = "ENSO_PARSER_PORT"

  val DEFAULT_PORT     = 30615
  val DEFAULT_HOSTNAME = "localhost"

  def from_env(): ParserServerConfig = {
    val hostname = sys.env.getOrElse(HOSTNAME_VAR, DEFAULT_HOSTNAME)
    val port = sys.env
      .get(PORT_VAR)
      .flatMap(str => Try { str.toInt }.toOption)
      .getOrElse(DEFAULT_PORT)
    ParserServerConfig(hostname, port)
  }
}

trait TextMessageServer {
  implicit val system: ActorSystem             = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def handleMessage(input: String): String

  val config: ParserServerConfig

  val handlerFlow: Flow[Message, TextMessage.Strict, NotUsed] =
    Flow[Message]
      .flatMapConcat {
        case tm: TextMessage =>
          val strict = tm.textStream.fold("")(_ + _)
          strict.map(input => TextMessage(handleMessage(input)))
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being
          // clogged
          bm.dataStream.runWith(Sink.ignore)
          Source.empty
      }

  val handleRequest: HttpRequest => HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path(requestedResource), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          upgrade.handleMessages(handlerFlow)
        case None =>
          HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  def start(): Unit = {
    val bindingFuture =
      Http().bindAndHandleSync(
        handleRequest,
        interface = config.interface,
        port      = config.port
      )

    implicit val ec = ExecutionContext.global
    bindingFuture.onComplete({
      case b @ Success(_) =>
        println(s"Server online at ${config.address_string()}")
      case Failure(exception) =>
        println(s"Failed to start server: $exception")
        system.terminate()
        System.exit(1)
    })
  }
}

object ParserService {
  case class Request(codeToParse: String)
  case class Response(serializedAst: String)
}

case class ParserService(config: ParserServerConfig) extends TextMessageServer {
  import ParserService._

  val parser = new Parser()
  def serializeAst(ast: AST.Module): String =
    ast.toString // FIXME: proper JSON serialization paired with Rust deserialization

  def handleRequest(request: Request): Response = {
    val resultAst     = parser.run(new Reader(request.codeToParse))
    val serializedAst = serializeAst(resultAst)
    Response(serializedAst)
  }

  override def handleMessage(input: String): String = {
    val ast = parser.run(new Reader(input))
    ast.toString
  }
}

object ParserServiceMain extends App {
  import ParserServerConfig._
  println("Getting configuration from environment...")
  val config = ParserServerConfig.from_env()
  println(s"Will serve ${config.address_string()}")
  println(
    s"To change configuration, restart with $HOSTNAME_VAR or " +
    s"$PORT_VAR variables set to desired values"
  )
  val service = ParserService(config)
  service.start()
}
