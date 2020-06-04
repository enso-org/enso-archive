package org.enso.polyglot.debugger.protocol.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.polyglot.debugger.protocol.{
  Binding,
  EvaluationFailure,
  EvaluationSuccess,
  ExitSuccess,
  ListBindingsResult
}

object ResponseFactory {
  def createEvaluationSuccess(
    result: Object
  )(implicit builder: FlatBufferBuilder): Int = {
    val resultOffset = ObjectReprFactory.create(result)
    EvaluationSuccess.createEvaluationSuccess(builder, resultOffset)
  }

  def createEvaluationFailure(
    exception: Exception
  )(implicit builder: FlatBufferBuilder): Int = {
    val excpetionOffset = ExceptionReprFactory.create(exception)
    EvaluationFailure.createEvaluationFailure(builder, excpetionOffset)
  }

  private def createBinding(name: String, value: Object)(
    implicit builder: FlatBufferBuilder
  ): Int = {
    val nameOffset  = builder.createString(name)
    val valueOffset = ObjectReprFactory.create(value)
    Binding.createBinding(builder, nameOffset, valueOffset)
  }

  def createListBindingsResult(
    bindings: Map[String, Object]
  )(implicit builder: FlatBufferBuilder): Int = {
    val bindingsArray = bindings.toSeq.map((createBinding _).tupled)
    val bindingsOffset =
      ListBindingsResult.createBindingsVector(builder, bindingsArray.toArray)
    ListBindingsResult.createListBindingsResult(builder, bindingsOffset)
  }

  def createExitSuccess()(implicit builder: FlatBufferBuilder): Int = {
    ExitSuccess.startExitSuccess(builder)
    ExitSuccess.endExitSuccess(builder)
  }
}
