package org.enso.polyglot.debugger

import org.enso.polyglot.debugger.protocol.{ExceptionRepr, ObjectRepr}

sealed trait Response
object ExitSuccess extends Response
case class ListBindingsResult(bindings: Map[String, ObjectRepr])
    extends Response
case class EvaluationSuccess(result: ObjectRepr)       extends Response
case class EvaluationFailure(exception: ExceptionRepr) extends Response
