package org.enso.languageserver.protocol.data.factory

import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.data.util.EnsoUUID

object EnsoUuidFactory {

  /**
    * Creates EnsoUUID inside a [[FlatBufferBuilder]].
    *
    * @param uuid a uuid to serialize
    * @param builder a builder
    * @return offset
    */
  def create(uuid: UUID)(implicit builder: FlatBufferBuilder): Int = {
    EnsoUUID.createEnsoUUID(
      builder,
      uuid.getLeastSignificantBits,
      uuid.getMostSignificantBits
    )
  }

  /**
    * Creates an [[EnsoUUID]] inside a [[FlatBufferBuilder]].
    *
    * @param uuid a uuid to serialize
    * @param builder a builder
    * @return offset
    */
  def create(uuid: EnsoUUID)(implicit builder: FlatBufferBuilder): Int = {
    EnsoUUID.createEnsoUUID(
      builder,
      uuid.leastSigBits(),
      uuid.mostSigBits()
    )
  }

}
