package org.enso.languageserver.session

import akka.actor.Actor
import org.enso.languageserver.data.ClientId
import org.enso.languageserver.event.{
  DataSessionInitialized,
  DataSessionTerminated,
  RpcSessionInitialized,
  RpcSessionTerminated,
  SessionEvent
}
import org.enso.languageserver.session.SessionManager.{
  DeliverToDataController,
  DeliverToRpcController
}

class SessionManager extends Actor {

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SessionEvent])
  }

  override def receive: Receive =
    running(Map.empty.withDefault(clientId => Session(clientId, None, None)))

  private def running(sessions: Map[ClientId, Session]): Receive = {
    case RpcSessionInitialized(s @ RpcSession(clientId, _)) =>
      context.become(
        running(
          sessions + (clientId -> sessions(clientId).attachRpcSession(s))
        )
      )

    case RpcSessionTerminated(RpcSession(clientId, _)) =>
      val updatedSessions =
        (sessions + (clientId -> sessions(clientId).detachRpcSession()))
          .filterNot(_._2.isSessionTerminated)
      context.become(running(updatedSessions))

    case DataSessionInitialized(s @ DataSession(clientId, _)) =>
      context.become(
        running(
          sessions + (clientId -> sessions(clientId).attachDataSession(s))
        )
      )

    case DataSessionTerminated(DataSession(clientId, _)) =>
      val updatedSessions =
        (sessions + (clientId -> sessions(clientId).detachDataSession()))
          .filterNot(_._2.isSessionTerminated)
      context.become(running(updatedSessions))

    case DeliverToRpcController(clientId, payload) =>
      sessions
        .get(clientId)
        .foreach(_.maybeRpcSession.foreach(_.rpcController ! payload))

    case DeliverToDataController(clientId, payload) =>
      sessions
        .get(clientId)
        .foreach(_.maybeDataSession.foreach(_.dataController ! payload))
  }

}

object SessionManager {

  case class DeliverToRpcController[A](clientId: ClientId, payload: A)

  case class DeliverToDataController[A](clientId: ClientId, payload: A)

}
