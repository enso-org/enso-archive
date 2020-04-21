package org.enso.languageserver.websocket.binary.factory

import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.factory.EnsoUuidFactory
import org.enso.languageserver.protocol.binary.session.SessionInit

object SessionInitFactory {

  def create(clientId: UUID)(implicit builder: FlatBufferBuilder): Int = {
    SessionInit.startSessionInit(builder)
    val id = EnsoUuidFactory.create(clientId)
    SessionInit.addIdentifier(builder, id)
    SessionInit.endSessionInit(builder)
  }

}
