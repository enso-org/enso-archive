package org.enso.gateway.protocol.response

import io.circe.generic.semiauto.deriveEncoder
import io.circe.Encoder
import org.enso.gateway.protocol.response.error.{Data, ErrorCode, ErrorMessage}
import org.enso.gateway.protocol.response.error.Data.{InitializeData, ParseData}
import io.circe.syntax._

/**
  * `ResponseError` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * [[org.enso.gateway.protocol.Response]] error
  */
sealed trait ResponseError {
  def code: Int

  def message: String

  def data: Option[Data]
}

object ResponseError {
  implicit val responseErrorEncoder: Encoder[ResponseError] = Encoder.instance {
    case parseError: ParseError                   => parseError.asJson
    case methodNotFoundError: MethodNotFoundError => methodNotFoundError.asJson
    case initializeError: InitializeError         => initializeError.asJson
    case unexpectedError: UnexpectedError         => unexpectedError.asJson
  }

  case class ParseError private (
    code: Int,
    message: String,
    data: Option[ParseData]
  ) extends ResponseError

  object ParseError {
    def apply(
      message: String         = ErrorMessage.invalidJson,
      data: Option[ParseData] = None
    ): ParseError =
      ParseError(ErrorCode.parseError, message, data)

    implicit val parseErrorEncoder: Encoder[ParseError] =
      deriveEncoder
  }

  case class MethodNotFoundError private (
    code: Int,
    message: String,
    data: Option[Data]
  ) extends ResponseError

  object MethodNotFoundError {
    def apply(
      message: String    = ErrorMessage.methodNotFound,
      data: Option[Data] = None
    ): MethodNotFoundError =
      MethodNotFoundError(ErrorCode.methodNotFound, message, data)

    implicit val methodNotFoundErrorEncoder: Encoder[MethodNotFoundError] =
      deriveEncoder
  }

  /**
    * [[org.enso.gateway.protocol.Requests.Initialize]] error
    */
  case class InitializeError private (
    code: Int,
    message: String,
    data: Option[InitializeData]
  ) extends ResponseError

  object InitializeError {
    def apply(
      message: String              = ErrorMessage.wrongJsonRpcVersion,
      data: Option[InitializeData] = None
    ): InitializeError =
      InitializeError(ErrorCode.unknownProtocolVersion, message, data)

    implicit val initializeErrorEncoder: Encoder[InitializeError] =
      deriveEncoder
  }

  case class UnexpectedError private (
    code: Int,
    message: String,
    data: Option[Data.Text]
  ) extends ResponseError

  object UnexpectedError {
    def apply(
      message: String         = ErrorMessage.unexpectedError,
      data: Option[Data.Text] = None
    ): UnexpectedError =
      UnexpectedError(ErrorCode.unknownErrorCode, message, data)

    implicit val unexpectedErrorEncoder: Encoder[UnexpectedError] =
      deriveEncoder
  }

}
