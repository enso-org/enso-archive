package org.enso.gateway.protocol.response

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.enso.gateway.protocol.response.error.{Data, ErrorCode, ErrorMessage}
import org.enso.gateway.protocol.response.error.Data.{InitializeData, ParseData}

/** Error of [[org.enso.gateway.protocol.Response]].
  *
  * `ResponseError` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  */
case class ResponseError private (
  code: ErrorCode,
  message: String,
  data: Option[Data]
)
object ResponseError {
  implicit val responseErrorEncoder: Encoder[ResponseError] = deriveEncoder
  implicit val responseErrorDecoder: Decoder[ResponseError] = deriveDecoder

  /** Invalid JSON. */
  def parseError(
    data: Option[ParseData] = None
  ): ResponseError = ResponseError(
    ErrorCode.ParseError,
    ErrorMessage.invalidJson,
    data
  )

  /** Unknown JSON-RPC method. */
  def methodNotFoundError(
    data: Option[Data] = None
  ): ResponseError = ResponseError(
    ErrorCode.MethodNotFound,
    ErrorMessage.methodNotFound,
    data
  )

  /** Error of [[org.enso.gateway.protocol.Requests.Initialize]].
    *
    * Wrong JSON-RPC version.
    */
  def initializeError(
    data: Option[InitializeData] = None
  ): ResponseError = ResponseError(
    ErrorCode.UnknownProtocolVersion,
    ErrorMessage.wrongJsonRpcVersion,
    data
  )

  def invalidRequest(
    data: Option[Data.Text] = None
  ): ResponseError = ResponseError(
    ErrorCode.InvalidRequest,
    ErrorMessage.invalidRequest,
    data
  )

  def serverNotInitialized(
    data: Option[Data.Text] = None
  ): ResponseError = ResponseError(
    ErrorCode.ServerNotInitialized,
    ErrorMessage.serverNotInitialized,
    data
  )

  /** Default type of errors. */
  def unexpectedError(
    data: Option[Data.Text] = None
  ): ResponseError = ResponseError(
    ErrorCode.UnknownErrorCode,
    ErrorMessage.unexpectedError,
    data
  )
}
