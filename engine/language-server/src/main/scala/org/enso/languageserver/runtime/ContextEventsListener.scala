package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorLogging, Props}
import org.enso.languageserver.data.{Client, Config}
import org.enso.languageserver.filemanager.Path
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.util.UnhandledLogging
import org.enso.polyglot.runtime.Runtime.Api

final class ContextEventsListener(
  config: Config,
  client: Client,
  contextId: ContextId
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classOf[Api.ExpressionValuesComputed]): Unit
  }

  override def receive: Receive = {
    case Api.ExpressionValuesComputed(`contextId`, apiUpdates) =>
      val updates = apiUpdates.flatMap { update =>
        getRuntimeUpdate(update) match {
          case None =>
            log.error(s"Failed to convert $update")
            None
          case runtimeUpdate =>
            runtimeUpdate
        }
      }
      client.actor ! ContextRegistryProtocol.ExpressionValuesComputed(
        contextId,
        updates
      )
  }

  private def getRuntimeUpdate(
    update: Api.ExpressionValueUpdate
  ): Option[ExpressionValueUpdate] = {
    update.methodCall match {
      case None =>
        Some(
          ExpressionValueUpdate(
            update.expressionId,
            update.expressionType,
            update.shortValue,
            None
          )
        )
      case Some(methodCall) =>
        getRuntimePointer(methodCall).map { pointer =>
          ExpressionValueUpdate(
            update.expressionId,
            update.expressionType,
            update.shortValue,
            Some(pointer)
          )
        }
    }
  }

  private def getRuntimePointer(
    pointer: Api.MethodPointer
  ): Option[MethodPointer] =
    getRelativePath(pointer.file).map { relativePath =>
      MethodPointer(
        file          = relativePath,
        definedOnType = pointer.definedOnType,
        name          = pointer.name
      )
    }

  private def getRelativePath(path: java.nio.file.Path): Option[Path] =
    config.contentRoots.view.flatMap {
      case (id, root) =>
        if (path.startsWith(root.toPath)) {
          Some(Path(id, root.toPath.relativize(path)))
        } else {
          None
        }
    }.headOption

}

object ContextEventsListener {

  /**
    * Creates a configuration object used to create a [[ContextEventsListener]].
    */
  def props(config: Config, client: Client, contextId: ContextId): Props =
    Props(new ContextEventsListener(config, client, contextId))
}
