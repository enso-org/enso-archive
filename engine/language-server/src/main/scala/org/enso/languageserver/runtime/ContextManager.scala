package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorRef, Props}
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.runtime.handler.CreateContextRequestHandler

import scala.concurrent.duration.FiniteDuration

/**
  * Manager is created per client, holds client's context ids, and
  * communicates with the runtime connector through intermediate
  * handlers.
  *
  * @param timeout request timeout
  * @param runtime reference to the runtime connector
  */
final class ContextManager(timeout: FiniteDuration, runtime: ActorRef)
    extends Actor {

  import ExecutionProtocol._

  override def receive: Receive =
    withContext(Set())

  def withContext(contexts: Set[ContextId]): Receive = {
    case msg: CreateContextRequest =>
      val handler =
        context.actorOf(CreateContextRequestHandler.props(timeout, runtime))
      handler.forward(msg)
      context.become(withContext(contexts + msg.contextId))
  }
}

object ContextManager {

  /**
    * Creates a configuration object used to create a [[ContextManager]].
    *
    * @param timeout request timeout
    * @param runtime reference to the runtime connector
    */
  def props(timeout: FiniteDuration, runtime: ActorRef): Props =
    Props(new ContextManager(timeout, runtime))
}
