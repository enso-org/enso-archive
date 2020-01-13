package org.enso.gateway.protocol

import io.circe.{ACursor, Decoder, DecodingFailure}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import org.enso.gateway.protocol.response.Result
import RequestOrNotification.{failure, jsonrpcError, selectParamsDecoder}

/**
  * Parent trait for [[Request]] and [[Notification]]
  */
sealed trait RequestOrNotification {

  /**
    * JSON-RPC Version
    *
    * @see [[org.enso.gateway.Protocol.jsonRpcVersion]]
    */
  def jsonrpc: String

  /**
    * The JSON-RPC method to be invoked
    */
  def method: String
}

object RequestOrNotification {

  /**
    * Error message about wrong JSON-RPC version
    */
  val jsonrpcError: String = s"jsonrpc must be $jsonRpcVersion"

  /**
    * @param method Name of method
    * @return Error message about unknown method
    */
  def unknownMethodError(method: String): String = s"Unknown method: $method"

  /**
    * @param message A message of failure
    * @tparam A The type of successful decoding result
    * @return Failure decoding result
    */
  def failure[A](message: String): Decoder.Result[A] =
    Left(DecodingFailure(message, List()))

  /**
    * @param method Name of method, which is discriminator
    * @return Circe decoder for requests or notifications
    */
  def selectRequestOrNotificationDecoder(
    method: String
  ): Decoder[_ <: RequestOrNotification] =
    method match {
      // All requests
      case Requests.Initialize.method =>
        Decoder[Request[InitializeParams]]

      // All notifications
      case Notifications.Initialized.method =>
        Decoder[Notification[InitializedParams]]

      case m =>
        Decoder.failedWithMessage(unknownMethodError(m))
    }

  /**
    * @param method Name of method, which is discriminator
    * @return Circe decoder for method params
    */
  def selectParamsDecoder[T <: Params](
    method: String
  ): Decoder[Option[T]] =
    (method match {
      // All requests
      case Requests.Initialize.method =>
        Decoder[Option[InitializeParams]]

      // All notifications
      case Notifications.Initialized.method =>
        Decoder[Option[InitializedParams]]

      case m =>
        Decoder.failedWithMessage(unknownMethodError(m))
    }).asInstanceOf[Decoder[Option[T]]]

  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    cursor => {
      val methodCursor = cursor.downField(Notification.methodField)
      Decoder[String]
        .tryDecode(methodCursor)
        .flatMap(selectRequestOrNotificationDecoder(_).apply(cursor))
    }
}

/**
  * `RequestMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#requestMessage
  *
  * @param jsonrpc JSON-RPC Version
  * @param id      The request id
  * @param method  The JSON-RPC method to be invoked
  * @param params  The method's params. A Structured value that holds the parameter values
  *                to be used during the invocation of the method
  * @tparam T Subtype of [[Params]] for a request with specific method
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
    val idCursor = cursor.downField(idField)
    for {
      id                 <- Decoder[Id].tryDecode(idCursor)
      notificationFields <- Decoder[Notification[T]].apply(cursor)
    } yield Request[T](
      notificationFields.jsonrpc,
      id,
      notificationFields.method,
      notificationFields.params
    )
  }
}

/**
  * `NotificationMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#notificationMessage
  * A processed notification message must not send a response back (they work like events). Therefore no `id`
  *
  * @param jsonrpc JSON-RPC Version
  * @param method  The JSON-RPC method to be invoked
  * @param params  The method's params. A Structured value that holds the parameter values
  *                to be used during the invocation of the method
  * @tparam T Subtype of [[Params]] for a notification with specific method
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
      val jsonrpcCursor = cursor.downField(jsonrpcField)
      val methodCursor  = cursor.downField(methodField)
      val paramsCursor  = cursor.downField(paramsField)
      // Field `jsonrpc` must be correct
      val jsonrpcResult = validateJsonrpc(jsonrpcCursor)
      val methodResult  = Decoder[String].tryDecode(methodCursor)
      // Discriminator is field `method`
      val paramsResult = methodResult
        .flatMap(selectParamsDecoder(_).tryDecode(paramsCursor))
      for {
        jsonrpc <- jsonrpcResult
        method  <- methodResult
        params  <- paramsResult
      } yield Notification[T](jsonrpc, method, params)
    }

  private def validateJsonrpc[T <: Params](
    jsonrpcCursor: ACursor
  ): Decoder.Result[String] = {
    Decoder[String].tryDecode(jsonrpcCursor).flatMap {
      case v @ `jsonRpcVersion` => Right(v)
      case _                    => failure(jsonrpcError)
    }
  }
}
