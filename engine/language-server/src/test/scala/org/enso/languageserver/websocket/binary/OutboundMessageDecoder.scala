package org.enso.languageserver.websocket.binary

import java.nio.ByteBuffer

import org.enso.languageserver.protocol.binary.envelope.OutboundMessage
import org.enso.languageserver.util.binary.BinaryDecoder

object OutboundMessageDecoder extends BinaryDecoder[OutboundMessage] {
  override def decode(bytes: ByteBuffer): OutboundMessage = {
    OutboundMessage.getRootAsOutboundMessage(bytes)
  }
}
