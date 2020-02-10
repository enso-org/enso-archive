package org.enso.jsonrpcserver
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor, Json}

object Bare {
  import io.circe.generic.auto._

  type Id = String
  sealed trait BareMessage

  case class Notification(method: String, params: Json)      extends BareMessage
  case class Request(method: String, id: Id, params: Json)   extends BareMessage
  case class ResponseResult(id: Option[Id], result: Json)    extends BareMessage
  case class ResponseError(id: Option[Id], error: ErrorData) extends BareMessage

  case class ErrorData(code: Int, message: String, data: Json)

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
}
