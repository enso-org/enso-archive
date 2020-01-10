package org.enso.gateway.protocol

import io.circe.{Decoder, DecodingFailure}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import org.enso.gateway.protocol.response.Result
import RequestOrNotification.{failure, jsonrpcError, unknownMethodError}

/**
  * Parent trait for [[Request]] and [[Notification]]
  */
sealed trait RequestOrNotification {

  /**
    * See [[org.enso.gateway.Protocol.jsonRpcVersion]]
    */
  def jsonrpc: String

  /**
    * Name of JSON-RPC method
    */
  def method: String
}

object RequestOrNotification {

  /**
    * Error message about wrong JSON-RPC version
    */
  val jsonrpcError = s"jsonrpc must be $jsonRpcVersion"

  /**
    * Error message about unknown method
    */
  def unknownMethodError(method: String) = s"Unknown method: $method"

  /**
    * Failure decoding result
    */
  def failure(message: String) = Left(DecodingFailure(message, List()))

  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    c => {
      c.downField(Notification.methodField)
        .as[String]
        .flatMap {
          // All requests
          case Requests.Initialize.method =>
            Decoder[Request[InitializeParams]].apply(c)

          // All notifications
          case Notifications.Initialized.method =>
            Decoder[Notification[InitializedParams]].apply(c)

          case method =>
            failure(unknownMethodError(method))
        }
    }
}

/**
  * `RequestMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#requestMessage
  */
case class Request[T <: Params](
  jsonrpc: String,
  id: Id,
  method: String,
  params: Option[T]
) extends RequestOrNotification {
  def response(result: Result): Response = Response.result(Some(id), result)
}

object Request {
  private val idField = "id"

  implicit def requestDecoder[T <: Params]: Decoder[Request[T]] = cursor => {
    for {
      id           <- cursor.downField(idField).as[Id]
      notification <- Decoder[Notification[T]].apply(cursor)
    } yield Request[T](
      notification.jsonrpc,
      id,
      notification.method,
      notification.params
    )
  }
}

/**
  * `NotificationMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#notificationMessage
  */
case class Notification[T <: Params](
  jsonrpc: String,
  method: String,
  params: Option[T]
) extends RequestOrNotification

object Notification {

  /**
    * Field `method`, which is discriminator
    */
  val methodField = "method"

  private val paramsField  = "params"
  private val jsonrpcField = "jsonrpc"

  // Circe decoder for notifications and notification fields of requests
  implicit def notificationDecoder[T <: Params]: Decoder[Notification[T]] =
    cursor => {
      // Field `jsonrpc` must be correct
      val jsonrpcResult = cursor.downField(jsonrpcField).as[String].flatMap {
        case v @ `jsonRpcVersion` => Right(v)
        case _                    => failure(jsonrpcError)
      }
      val methodResult = cursor.downField(methodField).as[String]
      val paramsCursor = cursor.downField(paramsField)
      // Discriminator is field `method`
      val paramsResult = methodResult
        .flatMap {
          // All requests
          case Requests.Initialize.method =>
            Decoder[Option[InitializeParams]].tryDecode(paramsCursor)

          // All notifications
          case Notifications.Initialized.method =>
            Decoder[Option[InitializedParams]].tryDecode(paramsCursor)

          case method =>
            failure(unknownMethodError(method))
        }
        .asInstanceOf[Decoder.Result[Option[T]]]
      for {
        jsonrpc <- jsonrpcResult
        method  <- methodResult
        params  <- paramsResult
      } yield Notification[T](jsonrpc, method, params)
    }
}
