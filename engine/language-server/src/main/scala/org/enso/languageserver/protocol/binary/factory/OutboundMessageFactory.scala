package org.enso.languageserver.protocol.binary.factory

import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.envelope.OutboundMessage
import org.enso.languageserver.protocol.binary.util.EnsoUUID

object OutboundMessageFactory {

  /**
    * Creates [[OutboundMessage]] inside a [[FlatBufferBuilder]].
    *
    * @param requestId a request id
    * @param maybeCorrelationId a optional correlation id
    * @param payloadType a payload type
    * @param payload a message payload
    * @param builder a flat buffer builder
    * @return offset
    */
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
