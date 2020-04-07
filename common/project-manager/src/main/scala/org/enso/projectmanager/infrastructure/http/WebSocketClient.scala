package org.enso.projectmanager.infrastructure.http

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.pattern.pipe
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import org.enso.projectmanager.infrastructure.http.WebSocketClient._

class WebSocketClient(address: String, receiver: ActorRef)(
  implicit system: ActorSystem
) {

  import system.dispatcher

  private var outboundChannel: ActorRef = _

  private val source: Source[Message, NotUsed] = Source
    .actorRef[String](
      completionMatcher,
      PartialFunction.empty,
      1,
      OverflowStrategy.fail
    )
    .mapMaterializedValue { actorRef =>
      outboundChannel = actorRef
      NotUsed
    }
    .map { txt: String =>
      TextMessage(txt)
    }

  private def completionMatcher: PartialFunction[Any, CompletionStrategy] = {
    case CloseWebSocket => CompletionStrategy.immediately
  }

  private val sink: Sink[Message, NotUsed] = Flow[Message]
    .map {
      case TextMessage.Strict(s) => WebSocketMessage(s)
    }
    .to(
      Sink.actorRef[WebSocketMessage](
        receiver,
        WebSocketStreamClosed,
        WebSocketStreamFailure(_)
      )
    )

  private val flow = Flow.fromSinkAndSource(sink, source)

  def connect(): Unit = {
    val (future, _) =
      Http()
        .singleWebSocketRequest(
          WebSocketRequest(address),
          flow
        )
    future
      .map {
        case ValidUpgrade(_, _) =>
          WebSocketConnected

        case InvalidUpgradeResponse(_, cause) =>
          WebSocketStreamFailure(new Exception(s"Cannot connect $cause"))
      }
      .pipeTo(receiver)
  }

  def send(message: String): Unit = outboundChannel ! message

  def disconnect(): Unit = outboundChannel ! CloseWebSocket

}

object WebSocketClient {

  private object CloseWebSocket

  case object WebSocketConnected

  case class WebSocketMessage(payload: String)

  case object WebSocketStreamClosed

  case class WebSocketStreamFailure(th: Throwable)

}
