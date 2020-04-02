package org.enso.languageserver.runtime.handler

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.runtime.ExecutionProtocol
import org.enso.polyglot.runtime.Runtime.Api

import scala.concurrent.duration.FiniteDuration

/**
  * A request handler for destroy context commands.
  *
  * @param timeout request timeout
  * @param runtime reference to the runtime conector
  */
final class DestroyContextHandler(
  timeout: FiniteDuration,
  runtime: ActorRef
) extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case ExecutionProtocol.DestroyContextRequest(contextId) =>
      runtime ! Api.Request(
        UUID.randomUUID(),
        Api.DestroyContextRequest(contextId)
      )
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

    case Api.Response(_, Api.DestroyContextResponse(contextId, errOpt)) =>
      errOpt match {
        case Some(Api.ContextDoesNotExistError()) =>
          replyTo ! ExecutionProtocol.AccessDeniedError
        case None =>
          replyTo ! ExecutionProtocol.DestroyContextResponse(contextId)
      }
      cancellable.cancel()
      context.stop(self)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)
}

object DestroyContextHandler {

  /**
    * Creates a configuration object used to create [[DestroyContextHandler]].
    *
    * @param timeout request timeout
    * @param runtime reference to the runtime conector
    */
  def props(timeout: FiniteDuration, runtime: ActorRef): Props =
    Props(new DestroyContextHandler(timeout, runtime))
}
