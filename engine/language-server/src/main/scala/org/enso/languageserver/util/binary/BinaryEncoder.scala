package org.enso.languageserver.util.binary

import java.nio.ByteBuffer

trait BinaryEncoder[-A] {

  def encode(msg: A): ByteBuffer

}

object BinaryEncoder {

  val empty: BinaryEncoder[ByteBuffer] = identity

}
