package org.enso.gateway.protocol

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}
import org.enso.gateway.protocol.JsonRpcController.jsonRpcVersion
import org.enso.gateway.protocol.codec.{
  MessageDecoder,
  MessageEncoder,
  NotificationDecoder,
  RequestDecoder,
  RequestOrNotificationDecoder,
  RequestOrNotificationEncoder,
  ResponseDecoder
}
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.response.{ResponseError, Result}

sealed trait Message {

  /** JSON-RPC Version.
    *
    * @see [[JsonRpcController.jsonRpcVersion]].
    */
  def jsonrpc: String
}

object Message {
  implicit val messageDecoder: Decoder[Message] = MessageDecoder.instance
  implicit val messageEncoder: Encoder[Message] = MessageEncoder.instance
}

/** Parent trait for [[Request]] and [[Notification]]. */
sealed trait RequestOrNotification extends Message {

  /** The JSON-RPC method to be invoked. */
  def method: String
}

object RequestOrNotification {
  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    RequestOrNotificationDecoder.instance
  implicit val requestOrNotificationEncoder: Encoder[RequestOrNotification] =
    RequestOrNotificationEncoder.instance
}

/** `RequestMessage` in LSP Spec.
  *
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#requestMessage
  *
  * @param jsonrpc JSON-RPC Version
  * @param id      The request id
  * @param method  The JSON-RPC method to be invoked
  * @param params  The method's params. A Structured value that holds the
  *                parameter values to be used during the invocation of the
  *                method
  * @tparam P Subtype of [[Params]] for a request with specific method
  */
case class Request[P <: Params](
  jsonrpc: String,
  id: Id,
  method: String,
  params: Option[P]
) extends RequestOrNotification
object Request {
  implicit def requestDecoder[P <: Params]: Decoder[Request[P]] =
    RequestDecoder.instance

  implicit def requestEncoder[P <: Params: Encoder]: Encoder[Request[P]] =
    //    new Encoder[Request[P]] {
    //      override def apply(request: Request[P]): Json = {
    //        val jsonrpcJson = Encoder[String].apply(request.jsonrpc)
    //        val idJson      = Encoder[Id].apply(request.id)
    //        val methodJson  = Encoder[String].apply(request.method)
    //        val paramsJson  = encoder.apply(request.params)
    //        Json.obj(
    //          "jsonrpc" -> jsonrpcJson,
    //          "id"      -> idJson,
    //          "method"  -> methodJson,
    //          "params"  -> paramsJson
    //        )
    //      }
    //    }
    //    Encoder.forProduct4("jsonrpc", "id", "method", "params")(
    //      request => (request.jsonrpc, request.id, request.method, request.params)
    //    )
    deriveEncoder[Request[P]]
}

/** `NotificationMessage` in LSP Spec.
  *
  * A processed notification message must not send a response back (they work
  * like events). Therefore no `id`.
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#notificationMessage
  *
  * @param jsonrpc JSON-RPC Version.
  * @param method  The JSON-RPC method to be invoked.
  * @param params  The method's params. A structured value that holds the
  *                parameter values to be used during the invocation of the
  *                method.
  * @tparam P Subtype of [[Params]] for a notification with specific method.
  */
case class Notification[P <: Params](
  jsonrpc: String,
  method: String,
  params: Option[P]
) extends RequestOrNotification
object Notification {
  implicit def notificationDecoder[P <: Params]: Decoder[Notification[P]] =
    NotificationDecoder.instance

  implicit def notificationEncoder[P <: Params: Encoder]
    : Encoder[Notification[P]] = deriveEncoder
}

/** `ResponseMessage` in LSP Spec.
  *
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * @param jsonrpc JSON-RPC Version.
  * @param id      The request id.
  * @param result  The result of a request. This member is required on success
  *                and must not exist if there was an error.
  * @param error   The error object in case a request fails.
  */
case class Response private (
  jsonrpc: String,
  id: Option[Id],
  result: Option[Result],
  error: Option[ResponseError]
) extends Message
object Response {

  /** Creates response with a result.
    *
    * @param id     Id of request.
    * @param result [[Result]] of response.
    * @return the response.
    */
  def result(
    id: Option[Id] = None,
    result: Result
  ): Response =
    Response(jsonRpcVersion, id, Some(result), None)

  /**
    */
  def emptyResult(
    id: Option[Id] = None
  ): Response =
    Response(jsonRpcVersion, id, None, None)

  /** Creates response with an error.
    *
    * @param id    Id of request.
    * @param error [[ResponseError]] of response.
    * @return the response.
    */
  def error(
    id: Option[Id] = None,
    error: ResponseError
  ): Response =
    Response(jsonRpcVersion, id, None, Some(error))

  implicit val responseEncoder: Encoder[Response] = deriveEncoder
  implicit def responseDecoder: Decoder[Response] = ResponseDecoder.instance
}
