package org.enso.projectmanager.infrastructure.http

import akka.actor.ActorRef

trait WebSocketConnection {

  def connect(): Unit

  def disconnect(): Unit

  def send(message: String): Unit

  def attachListener(listener: ActorRef): Unit

}

object WebSocketConnection {

  case object WebSocketConnected

  case class WebSocketMessage(payload: String)

  case object WebSocketStreamClosed

  case class WebSocketStreamFailure(th: Throwable)

}
