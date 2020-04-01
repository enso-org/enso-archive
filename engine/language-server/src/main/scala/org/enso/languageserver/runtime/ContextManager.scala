package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorRef, Props}
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.runtime.handler._

import scala.concurrent.duration.FiniteDuration

/**
  * Manager is created per client, holds client's contexts, and
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

  private def withContext(contexts: Set[ContextId]): Receive = {
    case CreateContextRequest =>
      val handler =
        context.actorOf(CreateContextHandler.props(timeout, runtime))
      val contextId = freshId(contexts)
      handler.forward(CreateContextRequest(contextId))
      context.become(withContext(contexts + contextId))
  }

  @annotation.tailrec
  private def freshId(contexts: Set[ContextId]): ContextId = {
    val nextId = IdGen.nextId
    if (contexts.contains(nextId)) freshId(contexts) else nextId
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
