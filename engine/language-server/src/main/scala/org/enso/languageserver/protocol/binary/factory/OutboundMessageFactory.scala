package org.enso.languageserver.protocol.binary.factory

import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.envelope.OutboundMessage
import org.enso.languageserver.protocol.binary.util.EnsoUUID

object OutboundMessageFactory {

  def create(
    requestId: UUID,
    maybeCorrelationId: Option[EnsoUUID],
    payloadType: Byte,
    payload: Int
  )(implicit builder: FlatBufferBuilder): Int = {
    OutboundMessage.startOutboundMessage(builder)
    val reqId = EnsoUuidFactory.create(requestId)
    OutboundMessage.addRequestId(builder, reqId)
    maybeCorrelationId.foreach { uuid =>
      val corId = EnsoUuidFactory.create(uuid)
      OutboundMessage.addCorrelationId(builder, corId)
    }
    OutboundMessage.addPayloadType(builder, payloadType)
    OutboundMessage.addPayload(builder, payload)
    OutboundMessage.endOutboundMessage(builder)
  }

}
