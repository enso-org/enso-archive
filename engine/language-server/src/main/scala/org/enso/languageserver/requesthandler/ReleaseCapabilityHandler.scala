package org.enso.languageserver.requesthandler

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.ClientApi.ReleaseCapability
import org.enso.languageserver.LanguageProtocol
import org.enso.languageserver.LanguageProtocol.{
  CapabilityReleaseBadRequest,
  CapabilityReleased
}
import org.enso.languageserver.data.{CapabilityRegistration, Client}
import org.enso.languageserver.jsonrpc.Errors.ServiceError
import org.enso.languageserver.jsonrpc._

import scala.concurrent.duration.FiniteDuration

class ReleaseCapabilityHandler(
  capabilityRouter: ActorRef,
  requestTimeout: FiniteDuration,
  client: Client
) extends Actor
    with ActorLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  private def requestStage: Receive = {
    case Request(ReleaseCapability, id, params: CapabilityRegistration) =>
      capabilityRouter ! LanguageProtocol.ReleaseCapability(client.id, params)
      context.system.scheduler
        .scheduleOnce(requestTimeout, self, RequestTimeout)
      context.become(responseStage(id, sender()))
  }
  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case RequestTimeout =>
      log.error(s"Releasing capability for ${client.id} timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case CapabilityReleased =>
      replyTo ! ResponseResult(ReleaseCapability, id, Unused)
      context.stop(self)

    case CapabilityReleaseBadRequest =>
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)
  }

}

object ReleaseCapabilityHandler {

  def props(
    capabilityRouter: ActorRef,
    requestTimeout: FiniteDuration,
    client: Client
  ): Props =
    Props(
      new ReleaseCapabilityHandler(capabilityRouter, requestTimeout, client)
    )

}
