package org.enso.languageserver.filemanager

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
    */
  case object UnwatchPath

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
}