package org.enso.languageserver.util.binary

/**
  * Base trait signaling decoding failure.
  */
sealed trait DecodingFailure

object DecodingFailure {

  /**
    * Signals empty payload in byte stream.
    */
  case object EmptyPayload extends DecodingFailure

  /**
    * Signals that data is corrupted.
    */
  case object DataCorrupted extends DecodingFailure

  /**
    * Represents undefined decoding failure.
    *
    * @param throwable a throwable
    */
  case class GenericDecodingFailure(throwable: Throwable)
      extends DecodingFailure

}
