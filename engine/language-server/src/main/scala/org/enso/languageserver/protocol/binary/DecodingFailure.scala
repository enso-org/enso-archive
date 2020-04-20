package org.enso.languageserver.protocol.binary

sealed trait DecodingFailure

object DecodingFailure {

  case object EmptyPayload extends DecodingFailure

  case object DataCorrupted extends DecodingFailure

  case class GenericDecodingFailure(throwable: Throwable)
      extends DecodingFailure

}
