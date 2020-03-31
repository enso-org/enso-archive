package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorRef, Props}
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.runtime.handler.CreateContextRequestHandler

import scala.concurrent.duration.FiniteDuration

final class ContextManager(timeout: FiniteDuration, runtime: ActorRef) extends Actor {

  import ExecutionProtocol._

  override def receive: Receive =
    withContext(Set())

  def withContext(contexts: Set[ContextId]): Receive = {
    case CreateContextRequest(contextId) if contexts.contains(contextId) =>
      sender() ! ExecutionContextExistsError

    case msg: CreateContextRequest =>
      val handler = context.actorOf(CreateContextRequestHandler.props(timeout, runtime))
      handler.forward(msg)
  }
}

object ContextManager {

  def props(timeout: FiniteDuration, runtime: ActorRef): Props =
    Props(new ContextManager(timeout, runtime))
}
