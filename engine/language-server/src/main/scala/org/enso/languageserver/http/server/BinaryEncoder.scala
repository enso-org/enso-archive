package org.enso.languageserver.http.server

import java.nio.ByteBuffer

trait BinaryEncoder[-A] {

  def encode(msg: A): ByteBuffer

}
