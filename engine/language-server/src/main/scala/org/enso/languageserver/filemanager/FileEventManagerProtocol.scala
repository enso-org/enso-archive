package org.enso.languageserver.filemanager

import akka.actor.ActorRef

object FileEventManagerProtocol {

  /**
    * Requests event manager to watch the path.
    *
    * @param path path to watch
    */
  case class WatchPath(path: Path, clients: Set[ActorRef])

  case object WatchPath {

    def apply(path: Path, client: ActorRef): WatchPath =
      WatchPath(path, Set(client))
  }

  /**
    * Returns result of a [[WatchPath]] request.
    *
    * @param result either file system failure or unit representing success
    */
  case class WatchPathResult(result: Either[FileSystemFailure, Unit])

  /**
    * Requests event manager to stop watching.
    */
  case class UnwatchPath(client: ActorRef)

  /**
    * Returns the result of a [[UnwatchPath]] request.
    *
    * @param result either file system failure or unit representing success
    */
  case class UnwatchPathResult(result: Either[FileSystemFailure, Unit])

  /**
    * Returns a file event result.
    *
    * @param result file event
    */
  case class FileEventResult(result: FileEvent)

  /**
    * Notifies about unrecoverable file watcher error.
    *
    * @param error last file watcher error
    */
  case class FileEventError(error: Throwable)
}
