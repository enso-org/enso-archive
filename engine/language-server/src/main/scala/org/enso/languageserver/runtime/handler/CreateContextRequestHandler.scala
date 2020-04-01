package org.enso.languageserver.runtime.handler

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.runtime.{ExecutionProtocol, IdGen}
import org.enso.polyglot.runtime.Runtime

import scala.concurrent.duration.FiniteDuration

/**
  * A request handler for [[ExecutionProtocol.CreateContextRequest]] commands.
  *
  * @param timeout request timeout
  * @param runtime reference to the [[RuntimeConnector]]
  */
final class CreateContextRequestHandler(
  timeout: FiniteDuration,
  runtime: ActorRef
) extends Actor
    with ActorLogging {

  import context.dispatcher, ExecutionProtocol._

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case CreateContextRequest(contextId) =>
      runtime ! Runtime.Api.CreateContextRequest(IdGen.nextId, contextId)
      val cancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(sender(), cancellable))
  }

  private def responseStage(
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case RequestTimeout =>
      replyTo ! RequestTimeout
      context.stop(self)

    case Runtime.Api.CreateContextResponse(_, contextId) =>
      replyTo ! CreateContextResponse(contextId)
      cancellable.cancel()
      context.stop(self)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)
}

object CreateContextRequestHandler {

  def props(timeout: FiniteDuration, runtime: ActorRef): Props =
    Props(new CreateContextRequestHandler(timeout, runtime))
}
