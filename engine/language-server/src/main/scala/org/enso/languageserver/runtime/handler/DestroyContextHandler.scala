package org.enso.languageserver.runtime.handler

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.runtime.ContextRegistryProtocol
import org.enso.languageserver.util.UnhandledLogging
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
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher, ContextRegistryProtocol._

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case msg: Api.DestroyContextRequest =>
      runtime ! Api.Request(UUID.randomUUID(), msg)
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
          replyTo ! ContextNotFound
        case None =>
          replyTo ! DestroyContextResponse(contextId)
      }
      cancellable.cancel()
      context.stop(self)
  }
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
