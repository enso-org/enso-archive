package org.enso.languageserver

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import org.enso.languageserver.jsonrpc.MessageHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class WebSocketServer(languageServer: ActorRef)(
  implicit val system: ActorSystem,
  implicit val materializer: Materializer
) {

  implicit val ec: ExecutionContext = system.dispatcher

  private val outgoingBufferSize: Int            = 10
  private val newConnectionPath: String          = ""
  private val lazyMessageTimeout: FiniteDuration = 10.seconds

  private def newUser(): Flow[Message, Message, NotUsed] = {
    val clientId = UUID.randomUUID()
    val clientActor =
      system.actorOf(Props(new ClientController(clientId, languageServer)))

    val messageHandler =
      system.actorOf(
        Props(new MessageHandler(ClientApi.protocol, clientActor))
      )
    clientActor ! ClientApi.WebConnect(messageHandler)

    languageServer ! LanguageProtocol.Connect(clientId, clientActor)

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message]
        .mapConcat({
          case textMsg: TextMessage => textMsg :: Nil
          case _: BinaryMessage     => Nil
        })
        .mapAsync(1)(
          _.toStrict(lazyMessageTimeout)
            .map(msg => MessageHandler.WebMessage(msg.text))
        )
        .to(
          Sink.actorRef[MessageHandler.WebMessage](
            messageHandler,
            MessageHandler.Disconnected, { _: Any =>
              MessageHandler.Disconnected
            }
          )
        )

    val outgoingMessages: Source[Message, NotUsed] =
      Source
        .actorRef[MessageHandler.WebMessage](
          PartialFunction.empty,
          PartialFunction.empty,
          outgoingBufferSize,
          OverflowStrategy.fail
        )
        .mapMaterializedValue { outActor =>
          messageHandler ! MessageHandler.Connected(outActor)
          NotUsed
        }
        .map(
          (outMsg: MessageHandler.WebMessage) => TextMessage(outMsg.message)
        )

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  private val route: Route = path(newConnectionPath) {
    get { handleWebSocketMessages(newUser()) }
  }

  def bind(interface: String, port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(route, interface, port)
}
