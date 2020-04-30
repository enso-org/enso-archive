package org.enso.interpeter.instrument

import org.enso.polyglot.runtime.Runtime.Api.StackItem

import scala.collection.mutable.Stack

case class ExecutionContextState(
  stack: Stack[StackItem],
  visualisations: VisualisationHolder
)

object ExecutionContextState {

  def empty = ExecutionContextState(Stack.empty, VisualisationHolder.empty)

}
