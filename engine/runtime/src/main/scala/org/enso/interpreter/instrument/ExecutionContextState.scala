package org.enso.interpreter.instrument

import org.enso.polyglot.runtime.Runtime.Api.StackItem

import scala.collection.mutable

/**
  * Represents a state of an execution context.
  *
  * @param stack the current call stack for the execution context
  * @param visualisations the holder of all visualisations attached to the
  *                       execution context
  */
case class ExecutionContextState(
  stack: mutable.Stack[Frame],
  visualisations: VisualisationHolder
)

object ExecutionContextState {

  /**
    * Returns empty state.
    */
  def empty: ExecutionContextState =
    ExecutionContextState(mutable.Stack.empty, VisualisationHolder.empty)
}

/**
  * Stack frame of the context.
  *
  * @param item the stack item.
  * @param cache the cache of this stack frame.
  */
case class Frame(item: StackItem, cache: RuntimeCache)

case object Frame {

  def apply(item: StackItem): Frame =
    new Frame(item, new RuntimeCache)
}
