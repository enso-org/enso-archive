package org.enso.languageserver.http.server

import akka.actor.ActorRef

object WebSocketControlProtocol {

  sealed trait WsEvent

  sealed trait WsCommand

  case object ConnectionClosed extends WsEvent

  case class ConnectionFailed(throwable: Throwable) extends WsEvent

  case class OutboundStreamEstablished(outboundChannel: ActorRef)
      extends WsEvent

  case object CloseConnection extends WsCommand

}
