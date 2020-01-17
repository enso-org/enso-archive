package org.enso.gateway.protocol.response.error

import io.circe.Encoder

sealed abstract class ErrorCode(val code: Int)

/**
  * Codes of [[org.enso.gateway.protocol.response.ResponseError]]
  */
object ErrorCode {

  // Defined by JSON RPC

  /**
    * Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text
    */
  case object ParseError extends ErrorCode(-32700)

  /**
    * The JSON sent is not a valid Request object
    */
  case object InvalidRequest extends ErrorCode(-32600)

  /**
    * The method does not exist / is not available
    */
  case object MethodNotFound extends ErrorCode(-32601)

  /**
    * Invalid method parameter(s)
    */
  case object InvalidParams extends ErrorCode(-32602)

  /**
    * Internal JSON-RPC error
    */
  case object InternalError extends ErrorCode(-32603)

  //-32000 to -32099 	Reserved for implementation-defined server-errors
  case object ServerErrorEnd extends ErrorCode(-32000)

  case object UnknownErrorCode extends ErrorCode(-32001)

  case object ServerNotInitialized extends ErrorCode(-32002)

  case object ServerErrorStart extends ErrorCode(-32099)

  // Defined by LSP

  case object RequestCancelled extends ErrorCode(-32800)

  case object ContentModified extends ErrorCode(-32801)

  // initialize
  case object UnknownProtocolVersion extends ErrorCode(1)

  implicit val errorCodeEncoder: Encoder[ErrorCode] =
    Encoder.encodeInt.contramap(_.code)
}
