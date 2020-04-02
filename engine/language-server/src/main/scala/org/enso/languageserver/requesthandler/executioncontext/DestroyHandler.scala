package org.enso.languageserver.requesthandler.executioncontext

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.jsonrpc.Errors.ServiceError
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.{AccessDeniedError}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.runtime.ExecutionApi._
import org.enso.languageserver.runtime.{
  ContextRegistryProtocol,
  ExecutionProtocol
}

import scala.concurrent.duration.FiniteDuration

/**
  * A request handler for `executionContext/destroy` commands.
  *
  * @param timeout request timeout
  * @param contextRegistry a reference to the context registry.
  */
class DestroyHandler(
  timeout: FiniteDuration,
  contextRegistry: ActorRef
) extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(
        ExecutionContextDestroy,
        id,
        params: ExecutionContextDestroy.Params
        ) =>
      contextRegistry ! ContextRegistryProtocol.DestroyContextRequest(
        sender(),
        params.contextId
      )
      val cancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender(), cancellable))
  }

  private def responseStage(
    id: Id,
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case RequestTimeout =>
      log.error(s"Request $id timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case ExecutionProtocol.DestroyContextResponse(_) =>
      replyTo ! ResponseResult(ExecutionContextDestroy, id, Unused)
      cancellable.cancel()
      context.stop(self)

    case ExecutionProtocol.AccessDeniedError =>
      replyTo ! ResponseError(Some(id), AccessDeniedError)
      cancellable.cancel()
      context.stop(self)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)
}

object DestroyHandler {

  /**
    * Creates configuration object used to create a [[DestroyHandler]].
    *
    * @param timeout request timeout
    * @param contextRegistry a reference to the context registry.
    */
  def props(timeout: FiniteDuration, contextRegistry: ActorRef): Props =
    Props(new DestroyHandler(timeout, contextRegistry))

}
