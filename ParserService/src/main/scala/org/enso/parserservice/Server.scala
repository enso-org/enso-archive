package org.enso.parserservice

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

object Server {
  final case class Config(interface: String, port: Int) {
    def addressString(): String = s"ws://$interface:$port"
  }
}

/** WebSocket server supporting synchronous request-response protocol.
  *
  * Server accepts a single Text Message from a peer and responds with
  * another Text Message.
  *
  * Incoming binary messages are ignored.
  *
  */
trait Server {
  implicit val system: ActorSystem             = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def handleMessage(input: String): String

  val config: Server.Config

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
          println("Establishing connection " + req.toString())
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

    bindingFuture.onComplete({
      case b @ Success(_) =>
        println(s"Server online at ${config.addressString()}")
      case Failure(exception) =>
        println(s"Failed to start server: $exception")
        system.terminate()
        System.exit(1)
    })(ExecutionContext.global)
  }
}
