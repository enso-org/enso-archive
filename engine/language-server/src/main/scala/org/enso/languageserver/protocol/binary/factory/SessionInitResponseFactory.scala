package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.session.SessionInitResponse

object SessionInitResponseFactory {

  /**
    * Creates [[SessionInitResponse]] inside a [[FlatBufferBuilder]].
    *
    * @param builder a flat buffers builder
    * @return offset
    */
  def create()(implicit builder: FlatBufferBuilder): Int = {
    SessionInitResponse.startSessionInitResponse(builder)
    SessionInitResponse.endSessionInitResponse(builder)
  }

}
