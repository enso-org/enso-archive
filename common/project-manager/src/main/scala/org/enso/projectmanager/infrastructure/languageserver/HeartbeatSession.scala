package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Scheduler}
import io.circe.parser._
import org.enso.projectmanager.data.Socket
import org.enso.projectmanager.infrastructure.http.WebSocketConnection.{
  WebSocketConnected,
  WebSocketMessage,
  WebSocketStreamClosed,
  WebSocketStreamFailure
}
import org.enso.projectmanager.infrastructure.http.WebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.HeartbeatSession.{
  HeartbeatTimeout,
  SocketClosureTimeout
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.ServerUnresponsive
import org.enso.projectmanager.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/**
  * Implements one ping-pong session.
  *
  * @param socket a server socket
  * @param timeout a session timeout
  * @param connectionFactory a web socket connection factory
  * @param scheduler a scheduler
  */
class HeartbeatSession(
  socket: Socket,
  timeout: FiniteDuration,
  connectionFactory: WebSocketConnectionFactory,
  scheduler: Scheduler
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  private val requestId = UUID.randomUUID()

  private val client = connectionFactory.createConnection(socket)

  override def preStart(): Unit = {
    client.attachListener(self)
    client.connect()
  }

  override def receive: Receive = pingStage

  private def pingStage: Receive = {
    case WebSocketConnected =>
      log.debug(s"Sending ping message to $socket")
      client.send(s"""
                     |{ 
                     |   "jsonrpc": "2.0",
                     |   "method": "heartbeat/ping",
                     |   "id": "$requestId",
                     |   "params": null
                     |}
                     |""".stripMargin)
      val cancellable = scheduler.scheduleOnce(timeout, self, HeartbeatTimeout)
      context.become(pongStage(cancellable))

    case WebSocketStreamFailure(th) =>
      log.error(s"An error occurred during connecting to websocket $socket", th)
      context.parent ! ServerUnresponsive
      client.disconnect()
      context.stop(self)
  }

  private def pongStage(cancellable: Cancellable): Receive = {
    case WebSocketMessage(payload) =>
      val maybeJson =
        parse(payload).flatMap(_.hcursor.downField("id").as[String])

      maybeJson match {
        case Left(error) =>
          log.error("An error occurred during parsing pong reply", error)

        case Right(id) =>
          if (id == requestId.toString) {
            log.debug(s"Received correct pong message from $socket")
            cancellable.cancel()
            client.disconnect()
            val closureTimeout =
              scheduler.scheduleOnce(timeout, self, SocketClosureTimeout)
            context.become(socketClosureStage(closureTimeout))
          } else {
            log.warning(s"Received unknown response $payload")
          }
      }

    case HeartbeatTimeout =>
      log.debug(s"Heartbeat timeout detected for $requestId")
      context.parent ! ServerUnresponsive
      client.disconnect()
      val closureTimeout =
        scheduler.scheduleOnce(timeout, self, SocketClosureTimeout)
      context.become(socketClosureStage(closureTimeout))

    case WebSocketStreamClosed =>
      context.parent ! ServerUnresponsive
      context.stop(self)

    case WebSocketStreamFailure(th) =>
      log.error(s"An error occurred during waiting for Pong message", th)
      context.parent ! ServerUnresponsive
      cancellable.cancel()
      client.disconnect()
      context.stop(self)
  }

  private def socketClosureStage(cancellable: Cancellable): Receive = {
    case WebSocketStreamClosed =>
      context.stop(self)
      cancellable.cancel()

    case WebSocketStreamFailure(th) =>
      log.error(s"An error occurred during closing web socket", th)
      context.stop(self)
      cancellable.cancel()

    case SocketClosureTimeout =>
      log.error(s"Socket closure timed out")
      context.stop(self)
  }

}

object HeartbeatSession {

  /**
    * Signals hearbeat timeout.
    */
  case object HeartbeatTimeout

  /**
    * Signals socket closure timeout.
    */
  case object SocketClosureTimeout

  /**
    * Creates a configuration object used to create a [[LanguageServerSupervisor]].
    *
    * @param socket a server socket
    * @param timeout a session timeout
    * @param connectionFactory a web socket connection factory
    * @param scheduler a scheduler
    * @return a configuration object
    */
  def props(
    socket: Socket,
    timeout: FiniteDuration,
    connectionFactory: WebSocketConnectionFactory,
    scheduler: Scheduler
  ): Props =
    Props(new HeartbeatSession(socket, timeout, connectionFactory, scheduler))

}
