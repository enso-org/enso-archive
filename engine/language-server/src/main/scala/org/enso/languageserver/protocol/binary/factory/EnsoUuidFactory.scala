package org.enso.languageserver.protocol.binary.factory

import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.util.EnsoUUID

object EnsoUuidFactory {

  /**
    * Creates EnsoUUID inside a [[FlatBufferBuilder]].
    *
    * @param uuid a uuid do serialize
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
    * Creates EnsoUUID inside a [[FlatBufferBuilder]].
    *
    * @param uuid a uuid do serialize
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
