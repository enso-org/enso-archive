package org.enso.polyglot.debugger

sealed trait Request
object ExitRequest                               extends Request
object ListBindingsRequest                       extends Request
case class EvaluationRequest(expression: String) extends Request
