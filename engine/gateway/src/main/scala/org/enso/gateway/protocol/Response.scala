package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.Protocol.jsonRpcVersion
import org.enso.gateway.protocol.response.Result
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * `ResponseMessage` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * @param jsonrpc JSON-RPC Version
  * @param id      The request id
  * @param result  The result of a request. This member is required on success and must not exist if there was an error
  * @param error   The error object in case a request fails
  */
case class Response private (
  jsonrpc: String,
  id: Option[Id],
  result: Option[Result],
  error: Option[response.Error]
)

object Response {

  /**
    * Create response with a result
    */
  def result(id: Option[Id] = None, result: Result): Response =
    Response(jsonRpcVersion, id, Some(result), None)

  /**
    * Create response with an error
    */
  def error(id: Option[Id] = None, error: response.Error): Response =
    Response(jsonRpcVersion, id, None, Some(error))

  implicit val responseEncoder: Encoder[Response] = deriveEncoder
  implicit val responseDecoder: Decoder[Response] = deriveDecoder
}
