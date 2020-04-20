package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.util.Error

object ErrorFactory {

  def createReceivedCorruptedDataError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      1,
      builder.createString("Received corrupted data")
    )

  def createReceivedEmptyPayloadError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      2,
      builder.createString("Received empty payload in the inbound message")
    )

  def createServiceError()(
    implicit builder: FlatBufferBuilder
  ): Int =
    Error.createError(
      builder,
      1000,
      builder.createString("Service error")
    )

}
