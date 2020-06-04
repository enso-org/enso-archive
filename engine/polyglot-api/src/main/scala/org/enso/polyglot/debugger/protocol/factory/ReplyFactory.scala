package org.enso.polyglot.debugger.protocol.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.polyglot.debugger.protocol.EvaluationSuccess

object ReplyFactory {
  def createEvaluationSuccess(
    result: Object
  )(implicit builder: FlatBufferBuilder): Int = {
    val resultOffset = ObjectReprFactory.create(result)
    EvaluationSuccess.createEvaluationSuccess(builder, resultOffset)
  }
}
