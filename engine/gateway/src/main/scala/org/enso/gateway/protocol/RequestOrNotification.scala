package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveConfiguredDecoder,
  deriveConfiguredEncoder
}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import org.enso.gateway.Protocol.DerivationConfig._
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * Parent trait for [[Request]] and [[Notification]]
  */
sealed trait RequestOrNotification {
  def jsonrpc: String

  private val msg = s"jsonrpc must be $jsonRpcVersion"
  require(jsonrpc == jsonRpcVersion, msg)
}

object RequestOrNotification {
  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    deriveConfiguredDecoder
  implicit val requestOrNotificationEncoder: Encoder[RequestOrNotification] =
    deriveConfiguredEncoder
}

/**
  * `RequestMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#requestMessage
  */
sealed trait Request extends RequestOrNotification {
  def id: Id

  def method: String

  def params: Option[Params]
}

/**
  * `NotificationMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#notificationMessage
  */
sealed trait Notification extends RequestOrNotification {
  def method: String

  def params: Option[Params]
}

/**
  * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialize
  *
  * @param jsonrpc JSON-RPC Version
  * @param id      The request id
  * @param method  Must be `initialize`
  * @param params  The method's params.
  */
case class initialize(
  jsonrpc: String,
  id: Id,
  method: String,
  params: Option[InitializeParams] = None
) extends Request

object initialize {
  implicit val initializeDecoder: Decoder[initialize] =
    deriveConfiguredDecoder
  implicit val initializeEncoder: Encoder[initialize] =
    deriveConfiguredEncoder
}

/**
  * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialized
  *
  * @param jsonrpc JSON-RPC Version
  * @param method  Must be `initialized`
  * @param params  The method's params.
  */
case class initialized(
  jsonrpc: String,
  method: String,
  params: Option[InitializedParams] = None
) extends Notification

object initialized {
  implicit val initializedDecoder: Decoder[initialized] =
    deriveConfiguredDecoder
  implicit val initializedEncoder: Encoder[initialized] =
    deriveConfiguredEncoder
}
