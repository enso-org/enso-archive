package org.enso.syntax.text

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

import scala.util.Try

case class ParserServerConfig(
  interface: String = ParserServerConfig.DEFAULT_HOSTNAME,
  port: Int         = ParserServerConfig.DEFAULT_PORT,
  resource: String  = ParserServerConfig.DEFAULT_RESOURCE
)

object ParserServerConfig {
  val HOSTNAME_ENV_VAR_NAME = "ENSO_PARSER_HOSTNAME"
  val PORT_ENV_VAR_NAME     = "ENSO_PARSER_PORT"
  val RESOURCE_ENV_VAR_NAME = "ENSO_PARSER_RESOURCE"

  val DEFAULT_PORT     = 30615
  val DEFAULT_HOSTNAME = "localhost"
  val DEFAULT_RESOURCE = "/"

  def from_env(): ParserServerConfig = {
    val hostname = sys.env.getOrElse(HOSTNAME_ENV_VAR_NAME, DEFAULT_HOSTNAME)
    val port = sys.env
      .get(PORT_ENV_VAR_NAME)
      .flatMap(str => Try { str.toInt }.toOption)
      .getOrElse(DEFAULT_PORT)
    val resource = sys.env.getOrElse(RESOURCE_ENV_VAR_NAME, DEFAULT_RESOURCE)
    ParserServerConfig(hostname, port, resource)
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
    case req @ HttpRequest(GET, Uri.Path(requestedResource), _, _, _)
        if requestedResource == config.resource =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(handlerFlow)
        case None =>
          HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  def run(): Unit = {
    val bindingFuture =
      Http().bindAndHandleSync(
        handleRequest,
        interface = config.interface,
        port      = config.port
      )

    println(
      s"Server online at ${config.interface}:${config.port}${config.resource}" +
      s"\nPress RETURN to stop..."
    )
    StdIn.readLine()

    import system.dispatcher // for the future transformations
    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
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
    ast.toString // FIXME: proper JSON serialziation paired with Rust deserialization

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

object Main extends App {
  val config  = ParserServerConfig.from_env()
  val service = ParserService(config)
  service.run()
}
