package org.enso.languageserver.util.binary

import java.nio.ByteBuffer

/**
  * A type class that provides a way to decode bytes to value of type `A`.
  *
  * @tparam A a result type
  */
trait BinaryDecoder[+A] {

  /**
    * Decodes bytes.
    *
    * @param bytes a byte buffer
    * @return either decoding failure or a value of type `A`
    */
  def decode(bytes: ByteBuffer): Either[DecodingFailure, A]

}
