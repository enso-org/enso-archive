package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.session.SessionInitResponse

object SessionInitResponseFactory {

  def create()(implicit builder: FlatBufferBuilder): Int = {
    SessionInitResponse.startSessionInitResponse(builder)
    SessionInitResponse.endSessionInitResponse(builder)
  }

}
