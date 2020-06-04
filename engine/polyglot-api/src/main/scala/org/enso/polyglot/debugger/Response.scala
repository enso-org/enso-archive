package org.enso.polyglot.debugger

import org.enso.polyglot.debugger.protocol.{ExceptionRepr, ObjectRepr}

/**
  * Represents a deserialized response returned from the debugger.
  *
  * It is a separate class from BinaryResponse to encapsulate Flatbuffers union handling logic in a type-safe way.
  */
sealed trait Response

/**
  * Represents an evaluation success.
  *
  * @param result over-the-wire representation of evaluation result
  */
case class EvaluationSuccess(result: ObjectRepr) extends Response

/**
  * Represents an evaluation failure.
  *
  * @param exception over-the-wire representation of the exception that caused the failure
  */
case class EvaluationFailure(exception: ExceptionRepr) extends Response

/**
  * Represents the returned list of bindings.
  *
  * @param bindings mapping from names to bound values
  */
case class ListBindingsResult(bindings: Map[String, ObjectRepr])
    extends Response

/**
  * Represents a successful termination of the debugging session.
  */
object ExitSuccess extends Response
