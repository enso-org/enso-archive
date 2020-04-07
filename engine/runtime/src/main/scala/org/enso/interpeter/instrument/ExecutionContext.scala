package org.enso.interpeter.instrument

import org.enso.polyglot.runtime.Runtime.Api.{ContextId, StackItem}

import scala.collection.mutable.Stack

/**
  * Represents an execution context.
  *
  * @param id the context id.
  */
case class ExecutionContext(id: ContextId)

/**
  * Storage for active execution contexts.
  */
class ExecutionContextManager {
  private var contexts: Map[ExecutionContext, Stack[StackItem]] = Map()

  /**
    * Creates a new context with a given id.
    *
    * @param id the context id.
    */
  def create(id: ContextId): Unit =
    contexts += ExecutionContext(id) -> Stack.empty

  /**
    * Destroys a context with a given id.
    * @param id the context id.
    */
  def destroy(id: ContextId): Unit =
    contexts -= ExecutionContext(id)

  /**
    * Gets a context with a given id.
    *
    * @param id the context id.
    * @return the context with the given id, if exists.
    */
  def get(id: ContextId): Option[ExecutionContext] =
    for {
      _ <- contexts.get(ExecutionContext(id))
    } yield ExecutionContext(id)

  /**
    * If the context exists, push the item on the stack.
    *
    * @param id the context id.
    * @param item stack item.
    */
  def push(id: ContextId, item: StackItem): Unit =
    for {
      stack <- contexts.get(ExecutionContext(id))
    } stack.push(item)

  /**
    * If the context exists, and stack not empty, pop the item from the stack.
    *
    * @param id the context id.
    */
  def pop(id: ContextId): Unit =
    for {
      stack <- contexts.get(ExecutionContext(id))
      if stack.nonEmpty
    } stack.pop()
}
