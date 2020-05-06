package org.enso.languageserver.protocol.data.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.data.util.Success

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

}
