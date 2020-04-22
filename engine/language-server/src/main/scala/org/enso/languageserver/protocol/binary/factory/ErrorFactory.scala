package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.util.Error

object ErrorFactory {

  /**
    * Creates ReceivedCorruptedDataError inside a [[FlatBufferBuilder]].
    *
    * @param builder a flat buffer builder
    * @return offset
    */
  def createReceivedCorruptedDataError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      1,
      builder.createString("Received corrupted data")
    )

  /**
    * Creates ReceivedEmptyPayloadError inside a [[FlatBufferBuilder]].
    *
    * @param builder a flat buffer builder
    * @return offset
    */
  def createReceivedEmptyPayloadError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      2,
      builder.createString("Received empty payload in the inbound message")
    )

  /**
    * Creates ServiceError inside a [[FlatBufferBuilder]].
    *
    * @param builder a flat buffer builder
    * @return offset
    */
  def createServiceError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      0,
      builder.createString("Service error")
    )

}
