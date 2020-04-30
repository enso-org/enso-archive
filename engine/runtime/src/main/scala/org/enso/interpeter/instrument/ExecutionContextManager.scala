package org.enso.interpeter.instrument

import org.enso.polyglot.runtime.Runtime.Api.{
  ContextId,
  ExpressionId,
  StackItem,
  VisualisationId
}

import scala.collection.mutable.Stack

/**
  * Storage for active execution contexts.
  */
class ExecutionContextManager {

  private var contexts: Map[ContextId, ExecutionContextState] =
    Map().withDefaultValue(ExecutionContextState.empty)

  /**
    * Creates a new context with a given id.
    *
    * @param id the context id.
    */
  def create(id: ContextId): Unit =
    contexts += id -> ExecutionContextState.empty

  /**
    * Destroys a context with a given id.
    * @param id the context id.
    */
  def destroy(id: ContextId): Unit =
    contexts -= id

  /**
    * Gets a context with a given id.
    *
    * @param id the context id.
    * @return the context with the given id, if exists.
    */
  def get(id: ContextId): Option[ContextId] =
    for {
      _ <- contexts.get(id)
    } yield id

  /**
    * Gets a stack for a given context id.
    *
    * @param id the context id.
    * @return the stack.
    */
  def getStack(id: ContextId): Stack[StackItem] =
    contexts(id).stack

  /**
    * Gets all execution contexts.
    *
    * @return all currently available execution contexsts.
    */
  def getAll: collection.MapView[ContextId, Stack[StackItem]] =
    contexts.view.mapValues(_.stack)

  /**
    * If the context exists, push the item on the stack.
    *
    * @param id the context id.
    * @param item stack item.
    * @return Unit representing success or None if the context does not exist.
    */
  def push(id: ContextId, item: StackItem): Option[Unit] =
    for {
      state <- contexts.get(id)
    } yield state.stack.push(item)

  /**
    * If the context exists and stack not empty, pop the item from the stack.
    *
    * @param id the context id.
    * @return stack item or None if the stack is empty or not exists.
    */
  def pop(id: ContextId): Option[StackItem] =
    for {
      state <- contexts.get(id)
      if state.stack.nonEmpty
    } yield state.stack.pop()

  def contains(contextId: ContextId): Boolean = contexts.contains(contextId)

  def upsertVisualisation(
    contextId: ContextId,
    visualisation: Visualisation
  ): Unit = {
    val state = contexts(contextId)
    state.visualisations.upsert(visualisation)
  }

  def getVisualisationById(
    contextId: ContextId,
    visualisationId: VisualisationId
  ): Option[Visualisation] =
    for {
      state         <- contexts.get(contextId)
      visualisation <- state.visualisations.getById(visualisationId)
    } yield visualisation

  def findVisualisationForExpression(
    contextId: ContextId,
    expressionId: ExpressionId
  ): List[Visualisation] =
    for {
      state         <- contexts.get(contextId).toList
      visualisation <- state.visualisations.find(expressionId)
    } yield visualisation

  def removeVisualisation(
    contextId: ContextId,
    expressionId: ExpressionId,
    visualisationId: VisualisationId
  ): Unit = {
    val state = contexts(contextId)
    state.visualisations.remove(visualisationId, expressionId)
  }

}
