package org.enso.languageserver.protocol.binary

import java.nio.ByteBuffer

import org.enso.languageserver.http.server.BinaryEncoder

object BinaryProtocolEncoder extends BinaryEncoder[ByteBuffer] {

  override def encode(msg: ByteBuffer): ByteBuffer = identity(msg)

}
