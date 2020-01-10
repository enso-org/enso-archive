package org.enso.gateway.protocol

import io.circe.{Decoder, DecodingFailure}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  InitializeParams,
  InitializedParams
}
import org.enso.gateway.protocol.response.Result

/**
  * Parent trait for [[Request]] and [[Notification]]
  */
sealed trait RequestOrNotification {
  def jsonrpc: String

  def method: String

  require(jsonrpc == jsonRpcVersion, RequestOrNotification.jsonrpcError)
}

object RequestOrNotification {
  val jsonrpcError = s"jsonrpc must be $jsonRpcVersion"

  def methodError[A](method: String): Decoder.Result[A] = {
    val err = s"Unknown method: $method"
    Left(DecodingFailure(err, List()))
  }

  implicit val requestOrNotificationDecoder: Decoder[RequestOrNotification] =
    c => {
      c.downField(Notification.methodField)
        .as[String]
        .flatMap {
          case Requests.Initialize.method =>
            Decoder[Request[InitializeParams]].apply(c)
          case Notifications.Initialized.method =>
            Decoder[Notification[InitializedParams]].apply(c)
          case method =>
            methodError(method)
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
  private val jsonrpcField = "jsonrpc"
  val methodField          = "method"
  private val paramsField  = "params"

  implicit def notificationDecoder[T <: Params]: Decoder[Notification[T]] =
    cursor => {
      val jsonrpcResult =
        cursor.downField(jsonrpcField).as[String]
      val methodResult = cursor.downField(methodField).as[String]
      val paramsCursor =
        cursor
          .downField(paramsField)
      val paramsResult = methodResult
        .flatMap {
          case Requests.Initialize.method =>
            Decoder[Option[InitializeParams]].tryDecode(paramsCursor)
          case Notifications.Initialized.method =>
            Decoder[Option[InitializedParams]].tryDecode(paramsCursor)
          case method =>
            RequestOrNotification.methodError(method)
        }
        .asInstanceOf[Decoder.Result[Option[T]]]
      for {
        jsonrpc <- jsonrpcResult
        method  <- methodResult
        params  <- paramsResult
      } yield Notification[T](jsonrpc, method, params)
    }
}

trait Requests {
  val method: String

  def unapply[T <: Params](
    request: Request[T]
  ): Option[(Id, Option[T])] =
    request.method match {
      case `method` =>
        Some((request.id, request.params))
      case _ => None
    }
}

object Requests {

  /**
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialize
    */
  object Initialize extends Requests {
    override val method = "initialize"
  }

}

trait Notifications {
  val method: String

  def unapply[T <: Params](
    request: Notification[T]
  ): Option[Option[T]] =
    request.method match {
      case `method` =>
        Some(request.params)
      case _ => None
    }
}

object Notifications {

  /**
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialized
    */
  object Initialized extends Notifications {
    override val method = "initialized"
  }

}
