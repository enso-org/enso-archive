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

  /**
    * Creates EvaluationSuccess inside a [[FlatBufferBuilder]].
    *
    * @param result result of evaluation
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def createEvaluationSuccess(
    result: Object
  )(implicit builder: FlatBufferBuilder): Int = {
    val resultOffset = ObjectReprFactory.create(result)
    EvaluationSuccess.createEvaluationSuccess(builder, resultOffset)
  }

  /**
    * Creates EvaluationFailure inside a [[FlatBufferBuilder]].
    *
    * @param exception exception that caused evaluation failure
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def createEvaluationFailure(
    exception: Exception
  )(implicit builder: FlatBufferBuilder): Int = {
    val excpetionOffset = ExceptionReprFactory.create(exception)
    EvaluationFailure.createEvaluationFailure(builder, excpetionOffset)
  }

  /**
    * Creates ListBindingsResult inside a [[FlatBufferBuilder]].
    *
    * @param bindings mapping of names to bound values
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def createListBindingsResult(
    bindings: Map[String, Object]
  )(implicit builder: FlatBufferBuilder): Int = {
    val bindingsArray = bindings.toSeq.map((createBinding _).tupled)
    val bindingsOffset =
      ListBindingsResult.createBindingsVector(builder, bindingsArray.toArray)
    ListBindingsResult.createListBindingsResult(builder, bindingsOffset)
  }

  /**
    * Creates ExitSuccess inside a [[FlatBufferBuilder]].
    *
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def createExitSuccess()(implicit builder: FlatBufferBuilder): Int = {
    ExitSuccess.startExitSuccess(builder)
    ExitSuccess.endExitSuccess(builder)
  }

  private def createBinding(name: String, value: Object)(
    implicit builder: FlatBufferBuilder
  ): Int = {
    val nameOffset  = builder.createString(name)
    val valueOffset = ObjectReprFactory.create(value)
    Binding.createBinding(builder, nameOffset, valueOffset)
  }
}
