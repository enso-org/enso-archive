package org.enso.languageserver.http.server

import java.nio.ByteBuffer

trait BinaryDecoder[+A] {

  def decode(bytes: ByteBuffer): A

}
