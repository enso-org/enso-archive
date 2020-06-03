package org.enso.interpreter.instrument.execution

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.polyglot.runtime.Runtime.Api

import scala.collection.mutable

case class Executable(
  contextId: Api.ContextId,
  stack: mutable.Stack[InstrumentFrame]
)
