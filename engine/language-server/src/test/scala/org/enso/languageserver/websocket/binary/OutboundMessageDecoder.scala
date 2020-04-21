package org.enso.languageserver.websocket.binary

import java.nio.ByteBuffer

import org.enso.languageserver.http.server.BinaryDecoder
import org.enso.languageserver.protocol.binary.envelope.OutboundMessage

object OutboundMessageDecoder extends BinaryDecoder[OutboundMessage] {
  override def decode(bytes: ByteBuffer): OutboundMessage = {
    OutboundMessage.getRootAsOutboundMessage(bytes)
  }
}
