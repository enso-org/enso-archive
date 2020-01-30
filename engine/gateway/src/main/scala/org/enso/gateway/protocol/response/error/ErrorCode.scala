package org.enso.gateway.protocol.response.error

import io.circe.{Decoder, Encoder}

/** Code of [[org.enso.gateway.protocol.response.ResponseError]]. */
sealed abstract class ErrorCode(val code: Int)
object ErrorCode {
  private val parseError             = -32700
  private val invalidRequest         = -32600
  private val methodNotFound         = -32601
  private val invalidParams          = -32602
  private val internalError          = -32603
  private val serverErrorEnd         = -32000
  private val unknownError           = -32001
  private val serverNotInitialized   = -32002
  private val serverErrorStart       = -32099
  private val requestCancelled       = -32800
  private val contentModified        = -32801
  private val unknownProtocolVersion = 1
  private val invalidErrorCode       = "Invalid error code"

  /** Signals that invalid JSON was received by the server.
    *
    * An error occurred on the server while parsing the JSON text.
    * Defined by JSON-RPC Spec.
    */
  case object ParseError extends ErrorCode(parseError)

  /** Signals that the JSON sent is not a valid Request object.
    *
    * Defined by JSON-RPC Spec.
    */
  case object InvalidRequest extends ErrorCode(invalidRequest)

  /** Signals that the method does not exist or is not available.
    *
    * Defined by JSON-RPC Spec.
    */
  case object MethodNotFound extends ErrorCode(methodNotFound)

  /** Signals that method parameters are invalid.
    *
    * Defined by JSON-RPC Spec.
    */
  case object InvalidParams extends ErrorCode(invalidParams)

  /** Internal JSON-RPC error.
    *
    * Defined by JSON-RPC Spec.
    */
  case object InternalError extends ErrorCode(internalError)

  /** Codes from -32000 to -32099 reserved for implementation-defined
    * server-errors.
    */
  case object ServerErrorEnd extends ErrorCode(serverErrorEnd)

  case object UnknownErrorCode extends ErrorCode(unknownError)

  case object ServerNotInitialized extends ErrorCode(serverNotInitialized)

  case object ServerErrorStart extends ErrorCode(serverErrorStart)

  /** Error codes defined by LSP. */
  case object RequestCancelled extends ErrorCode(requestCancelled)

  case object ContentModified extends ErrorCode(contentModified)

  /** Error code for `initialize` method. */
  case object UnknownProtocolVersion extends ErrorCode(unknownProtocolVersion)

  implicit val errorCodeEncoder: Encoder[ErrorCode] =
    Encoder.encodeInt.contramap(_.code)
  implicit val errorCodeDecoder: Decoder[ErrorCode] =
    Decoder.decodeInt.emap {
      case `parseError`             => Right(ParseError)
      case `invalidRequest`         => Right(InvalidRequest)
      case `methodNotFound`         => Right(MethodNotFound)
      case `invalidParams`          => Right(InvalidParams)
      case `internalError`          => Right(InternalError)
      case `serverErrorEnd`         => Right(ServerErrorEnd)
      case `unknownError`           => Right(UnknownErrorCode)
      case `serverNotInitialized`   => Right(ServerNotInitialized)
      case `serverErrorStart`       => Right(ServerErrorStart)
      case `requestCancelled`       => Right(RequestCancelled)
      case `contentModified`        => Right(ContentModified)
      case `unknownProtocolVersion` => Right(UnknownErrorCode)
      case _                        => Left(invalidErrorCode)
    }
}
