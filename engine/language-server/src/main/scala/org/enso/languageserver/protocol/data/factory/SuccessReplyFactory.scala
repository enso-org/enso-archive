package org.enso.languageserver.protocol.data.factory

import java.nio.ByteBuffer
import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.data.envelope.OutboundPayload
import org.enso.languageserver.protocol.data.util.{EnsoUUID, Success}

object SuccessReplyFactory {

  /**
    * Creates a [[Success]] inside a [[FlatBufferBuilder]].
    *
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def create()(implicit builder: FlatBufferBuilder): Int = {
    Success.startSuccess(builder)
    Success.endSuccess(builder)
  }

  def createPacket(requestId: EnsoUUID): ByteBuffer = {
    implicit val builder = new FlatBufferBuilder(1024)
    val outMsg = OutboundMessageFactory.create(
      UUID.randomUUID(),
      Some(requestId),
      OutboundPayload.SUCCESS,
      SuccessReplyFactory.create()
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

}
