package org.enso.jsonrpcserver
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

object Bare {
  import io.circe.generic.auto._
  import io.circe.syntax._

  type Id = String
  sealed trait BareMessage

  case class Notification(method: String, params: Json)      extends BareMessage
  case class Request(method: String, id: Id, params: Json)   extends BareMessage
  case class ResponseResult(id: Option[Id], result: Json)    extends BareMessage
  case class ResponseError(id: Option[Id], error: ErrorData) extends BareMessage

  case class ErrorData(code: Int, message: String)

  implicit val responseEncoder: Encoder[ResponseResult] = (a: ResponseResult) =>
    Json.obj(
      "jsonrpc" -> "2.0".asJson,
      "id"      -> a.id.asJson,
      "result"  -> a.result
    )

  implicit val notificationEncoder: Encoder[Notification] = (n: Notification) =>
    Json.obj(
      "jsonrpc" -> "2.0".asJson,
      "method"  -> n.method.asJson,
      "params"  -> n.params
    )

  implicit val errorEncoder: Encoder[ResponseError] = (r: ResponseError) =>
    Json.obj(
      "jsonrpc" -> "2.0".asJson,
      "id"      -> r.id.asJson,
      "error"   -> r.error.asJson
    )

  implicit val requestEncoder: Encoder[Request] = (r: Request) =>
    Json.obj(
      "jsonrpc" -> "2.0".asJson,
      "id"      -> r.id.asJson,
      "method"  -> r.method.asJson,
      "params"  -> r.params
    )

  implicit val bareMessageEncoder: Encoder[BareMessage] = {
    case request: Request               => request.asJson
    case responseError: ResponseError   => responseError.asJson
    case responseResult: ResponseResult => responseResult.asJson
    case notification: Notification     => notification.asJson
  }

  implicit val decoder: Decoder[BareMessage] = new Decoder[BareMessage] {
    val expectedNotificationKeys: Set[String] =
      Set("jsonrpc", "method", "params")
    val expectedRequestKeys: Set[String] =
      Set("jsonrpc", "method", "params", "id")
    val expectedResponseResultKeys: Set[String] =
      Set("jsonrpc", "id", "result")
    val expectedResponseErrorKeys: Set[String] = Set("jsonrpc", "id", "error")

    override def apply(c: HCursor): Result[BareMessage] = {
      val jsonRpcValid = c
          .downField("jsonrpc")
          .as[String] == Right("2.0")
      if (!jsonRpcValid) {
        return Left(
          DecodingFailure("Invalid JSON RPC version manifest.", List())
        )
      }
      val fields = c.keys.getOrElse(List()).toSet
      if (fields == expectedRequestKeys) {
        c.as[Request]
      } else if (fields == expectedNotificationKeys) {
        c.as[Notification]
      } else if (fields == expectedResponseResultKeys) {
        c.as[ResponseResult]
      } else if (fields == expectedResponseErrorKeys) {
        c.as[ResponseError]
      } else {
        Left(DecodingFailure("Malformed JSON RPC message.", List()))
      }
    }
  }

  def parse(a: String): Option[BareMessage] = {
    io.circe.parser.parse(a).toOption.flatMap(_.as[BareMessage].toOption)
  }

  def encode(msg: BareMessage): String = msg.asJson.toString

}
