package org.enso.languageserver.util.binary

import java.nio.ByteBuffer

trait BinaryDecoder[+A] {

  def decode(bytes: ByteBuffer): Either[DecodingFailure, A]

}
