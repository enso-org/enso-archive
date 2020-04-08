package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.filemanager.Path
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.util.UnhandledLogging
import org.enso.polyglot.runtime.Runtime.Api

final class ContextEventsListener(
  client: ActorRef,
  root: Path,
  contextId: ContextId
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classOf[Api.ExpressionValuesComputed]): Unit
  }

  override def receive: Receive = {
    case Api.ExpressionValuesComputed(`contextId`, updates) =>
      client ! ContextRegistryProtocol.ExpressionValuesComputed(
        contextId,
        updates.map(fromRuntimeUpdate)
      )
  }

  private def fromRuntimeUpdate(
    update: Api.ExpressionValueUpdate
  ): ExpressionValueUpdate =
    ExpressionValueUpdate(
      update.expressionId,
      update.expressionType,
      update.shortValue,
      update.methodCall.map(fromRuntimePointer)
    )

  private def fromRuntimePointer(
    pointer: Api.MethodPointer
  ): MethodPointer =
      MethodPointer(
        file          = Path.getRelativePath(root.toFile, root, pointer.file),
        definedOnType = pointer.definedOnType,
        name          = pointer.name
      )
}

object ContextEventsListener {

  /**
    * Creates a configuration object used to create a [[ContextEventsListener]].
    */
  def props(client: ActorRef, root: Path, contextId: ContextId): Props =
    Props(new ContextEventsListener(client, root, contextId))
}
