package org.enso.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import org.enso.languageserver.jsonrpc.{
  Error,
  HasParams,
  HasResult,
  MessageHandler,
  Method,
  Notification,
  Protocol,
  Request,
  ResponseError,
  ResponseResult,
  Unused
}

import scala.concurrent.ExecutionContext
import scala.util.Success
import akka.util.Timeout
import akka.pattern.ask
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor, Json}
import org.enso.languageserver.JsonRpcApi.{
  AcquireCapability,
  ForceReleaseCapability,
  GrantCapability,
  ReleaseCapability,
  ReleaseCapabilityParams
}
import org.enso.languageserver.data.CapabilityRegistration

import scala.concurrent.duration._

object JsonRpcApi {
  import io.circe.generic.auto._

  case object CantCompleteRequestError
      extends Error(1, "Can't complete request")

  case object AcquireCapability extends Method("capability/acquire") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = CapabilityRegistration
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case class ReleaseCapabilityParams(id: UUID)

  case object ReleaseCapability extends Method("capability/release") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = ReleaseCapabilityParams
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ForceReleaseCapability extends Method("capability/forceRelease") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = ReleaseCapabilityParams
    }
  }

  case object GrantCapability extends Method("capability/grant") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = CapabilityRegistration
    }
  }

  val protocol: Protocol = Protocol.empty
    .registerRequest(AcquireCapability)
    .registerRequest(ReleaseCapability)
    .registerNotification(ForceReleaseCapability)
    .registerNotification(GrantCapability)
    .registerError(CantCompleteRequestError)

  case class WsConnect(webActor: ActorRef)
}

class Client(val clientId: LanguageProtocol.ClientId, val server: ActorRef)
    extends Actor
    with Stash
    with ActorLogging {
  implicit val timeout: Timeout     = 5.seconds
  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case JsonRpcApi.WsConnect(webActor) =>
      log.debug("WebSocket connected.")
      unstashAll()
      context.become(connected(webActor))
    case _ => stash()
  }

  def connected(webActor: ActorRef): Receive = {
    case MessageHandler.Disconnected =>
      log.debug("WebSocket disconnected.")
      server ! LanguageProtocol.Disconnect(clientId)
      context.stop(self)

    case LanguageProtocol.ForceReleaseCapability(id) =>
      webActor ! Notification(
        ForceReleaseCapability,
        ReleaseCapabilityParams(id)
      )

    case LanguageProtocol.GrantCapability(registration) =>
      webActor ! Notification(GrantCapability, registration)

    case Request(AcquireCapability, id, registration: CapabilityRegistration) =>
      server ! LanguageProtocol.AcquireCapability(clientId, registration)
      sender ! ResponseResult(AcquireCapability, id, Unused)

    case Request(ReleaseCapability, id, params: ReleaseCapabilityParams) =>
      server ! LanguageProtocol.ReleaseCapability(clientId, params.id)
      sender ! ResponseResult(ReleaseCapability, id, Unused)
  }
}
