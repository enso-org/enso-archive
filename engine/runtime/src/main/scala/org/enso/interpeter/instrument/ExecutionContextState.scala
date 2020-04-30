package org.enso.interpeter.instrument

import java.util.UUID

import org.enso.polyglot.runtime.Runtime.Api.StackItem

import scala.collection.mutable.Stack

case class ExecutionContextState(
  stack: Stack[StackItem],
  visualisations: Map[UUID, Visualisation]
) {

  def addVisualisation(visualisation: Visualisation): ExecutionContextState =
    this.copy(visualisations =
      visualisations + (visualisation.expressionId -> visualisation)
    )

}

object ExecutionContextState {

  def empty = ExecutionContextState(Stack.empty, Map.empty)

}
