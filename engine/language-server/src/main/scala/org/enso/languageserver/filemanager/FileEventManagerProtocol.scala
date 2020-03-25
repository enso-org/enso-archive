package org.enso.languageserver.filemanager

import akka.actor.ActorRef

object FileEventManagerProtocol {

  /**
    * Requests event manager to watch the path.
    *
    * @param path path to watch
    */
  case class WatchPath(path: Path)

  /**
    * Returns result of a [[WatchPath]] request.
    *
    * @param result either file system failure or unit representing success
    */
  case class WatchPathResult(result: Either[FileSystemFailure, Unit])

  /**
    * Requests event manager to stop watching.
    *
    * @param handler reference to the request handler
    */
  case class UnwatchPath(handler: ActorRef)

  /**
    * Returns the result of a [[UnwatchPath]] request.
    *
    * @param handler reference to the request handler
    * @param result either file system failure or unit representing success
    */
  case class UnwatchPathResult(
    handler: ActorRef,
    result: Either[FileSystemFailure, Unit]
  )

  /**
    * Returns a file event result.
    *
    * @param result file event
    */
  case class FileEventResult(result: FileEvent)
}
