package org.enso.gateway.protocol

import io.circe.{ACursor, Decoder, DecodingFailure}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import RequestOrNotification.selectParamsDecoder
import io.circe.CursorOp.DownField

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
        Decoder.failed(
          unknownMethodFailure(m)
        )
    }

  /**
    * @param method Name of method. It is the discriminator
    * @return Circe decoder for method params
    */
  def selectParamsDecoder[P <: Params](
    method: String
  ): Decoder[Option[P]] =
    (method match {
      // All requests
      case Requests.Initialize.method =>
        Decoder[Option[InitializeParams]]

      // All notifications
      case Notifications.Initialized.method =>
        Decoder[Option[InitializedParams]]

      case m =>
        Decoder.failed(
          unknownMethodFailure(m)
        )
    }).asInstanceOf[Decoder[Option[P]]]

  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    cursor => {
      val methodCursor = cursor.downField(Notification.methodField)
      Decoder[String]
        .tryDecode(methodCursor)
        .flatMap(selectRequestOrNotificationDecoder(_).apply(cursor))
    }

  private class UnknownMethodError(method: String)
      extends RuntimeException(s"Unknown method $method")

  private def unknownMethodFailure(method: String): DecodingFailure =
    DecodingFailure
      .fromThrowable(
        new UnknownMethodError(method),
        List(DownField(Notification.methodField))
      )
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
  * @tparam P Subtype of [[Params]] for a request with specific method
  */
case class Request[P <: Params](
  jsonrpc: String,
  id: Id,
  method: String,
  params: Option[P]
) extends RequestOrNotification

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
  * @param params  The method's params. A structured value that holds the parameter values
  *                to be used during the invocation of the method
  * @tparam P Subtype of [[Params]] for a notification with specific method
  */
case class Notification[P <: Params](
  jsonrpc: String,
  method: String,
  params: Option[P]
) extends RequestOrNotification

object Notification {

  /**
    * Field `method`, which is discriminator
    */
  val methodField = "method"

  val jsonrpcField = "jsonrpc"

  private val paramsField = "params"

  // Circe decoder for notifications and notification fields of requests
  implicit def notificationDecoder[P <: Params]: Decoder[Notification[P]] =
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
      } yield Notification[P](jsonrpc, method, params)
    }

  private def wrongJsonRpcVersionFailure(
    version: String,
    jsonrpcCursor: ACursor
  ): DecodingFailure =
    DecodingFailure
      .fromThrowable(
        new WrongJsonRpcVersion(version),
        jsonrpcCursor.history
      )

  private def validateJsonrpc[P <: Params](
    jsonrpcCursor: ACursor
  ): Decoder.Result[String] = {
    Decoder[String].tryDecode(jsonrpcCursor).flatMap {
      case version @ `jsonRpcVersion` => Right(version)
      case version =>
        Left(
          wrongJsonRpcVersionFailure(version, jsonrpcCursor)
        )
    }
  }

  private class WrongJsonRpcVersion(version: String)
      extends RuntimeException(
        s"jsonrpc must be $jsonRpcVersion but found $version"
      )

}
