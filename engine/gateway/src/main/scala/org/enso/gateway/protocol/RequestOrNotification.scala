package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._
import org.enso.gateway.protocol.response.Result
import shapeless.TypeCase
import cats.syntax.functor._

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
    List[Decoder[RequestOrNotification]](
      Decoder[Request].widen,
      Decoder[Notification].widen
    ).reduceLeft(_ or _)
}

/**
  * `RequestMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#requestMessage
  */
case class Request(
  jsonrpc: String,
  id: Id,
  method: String,
  params: Option[Params]
) extends RequestOrNotification {
  def response(result: Result): Response = Response.result(Some(id), result)
}

object Request {
  implicit val requestDecoder: Decoder[Request] =
    deriveDecoder
  implicit val requestEncoder: Encoder[Request] =
    deriveEncoder
}

/**
  * `NotificationMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#notificationMessage
  */
case class Notification(
  jsonrpc: String,
  method: String,
  params: Option[Params]
) extends RequestOrNotification

object Notification {
  implicit val notificationDecoder: Decoder[Notification] =
    deriveDecoder
  implicit val notificationEncoder: Encoder[Notification] =
    deriveEncoder
}

/**
  * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialize
  */
object Initialize {
  private val method                     = "initialize"
  private val `Option[InitializeParams]` = TypeCase[Option[InitializeParams]]

  def unapply(
    request: Request
  ): Option[(Id, Option[InitializeParams])] =
    (request.method, request.params) match {
      case (`method`, `Option[InitializeParams]`(params)) =>
        Some((request.id, params))
      case _ => None
    }
}

/**
  * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialized
  */
object Initialized {
  private val method                      = "initialized"
  private val `Option[InitializedParams]` = TypeCase[Option[InitializedParams]]

  def unapply(request: Notification): Option[Option[InitializedParams]] =
    (request.method, request.params) match {
      case (`method`, `Option[InitializedParams]`(params)) =>
        Some(params)
      case _ => None
    }
}
