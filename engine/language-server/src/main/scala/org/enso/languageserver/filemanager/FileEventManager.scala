package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import zio.ZIO
import zio.blocking.effectBlocking

import scala.util.Try

/**
  * Event manager starts [[FileEventWatcher]], handles errors, converts and
  * sends [[FileEvent]]'s to subscriber.
  *
  * @param config configuration
  * @param fs file system api
  * @param exec executor of file system effects
  */
final class FileEventManager(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor {

  import context.dispatcher, FileEventManagerProtocol._

  override def receive: Receive = uninitializedStage

  private def uninitializedStage: Receive = {
    case WatchPath(path) =>
      config.findContentRoot(path.rootId) match {
        case Right(rootPath) =>
          val pathToWatch = path.toFile(rootPath).toPath
          val watcher     = new FileEventWatcher(pathToWatch, self ! _)
          val result = Try(exec.exec_(effectBlocking(watcher.start())))
            .fold(resultFailure, resultSuccess)
          sender() ! WatchPathResult(result)
          context.become(initializedStage(rootPath, path, watcher, sender()))

        case Left(err) =>
          sender() ! WatchPathResult(Left(err))
      }
  }

  private def initializedStage(
    root: File,
    path: Path,
    watcher: FileEventWatcher,
    replyTo: ActorRef
  ): Receive = {
    case UnwatchPath =>
      val result = Try(watcher.stop()).fold(resultFailure, resultSuccess)
      sender() ! UnwatchPathResult(result)
      context.unbecome()

    case event: FileEventWatcherApi.WatcherEvent =>
      exec
        .execTimed(config.fileManager.timeout, readFileEvent(root, path, event))
        .map(FileEventResult)
        .pipeTo(replyTo)
      ()
  }

  /**
    * Conversion from system events.
    *
    * @param event system event
    * @return watcher events
    */
  private def readFileEvent(
    root: File,
    path: Path,
    event: FileEventWatcherApi.WatcherEvent
  ): BlockingIO[FileSystemFailure, FileEvent] =
    readEventEntry(event.path)
      .map({ entry =>
        FileEvent(
          FileSystemObject.fromEntry(root, path, entry),
          FileEventKind(event.eventType)
        )
      })

  private def readEventEntry(
    path: java.nio.file.Path
  ): BlockingIO[FileSystemFailure, FileSystemApi.Entry] =
    fs.readEntry(path)
      .flatMap({
        case FileSystemApi.SymbolicLinkEntry(path, _) =>
          fs.readSymbolicLinkEntry(path)
        case entry =>
          ZIO.succeed(entry)
      })

  private def resultSuccess[A](value: A): Either[FileSystemFailure, A] =
    Right(value)

  private def resultFailure[A](t: Throwable): Either[FileSystemFailure, A] =
    Left(GenericFileSystemFailure(t.getMessage()))
}

object FileEventManager {

  def props(
    config: Config,
    fs: FileSystemApi[BlockingIO],
    exec: Exec[BlockingIO]
  ): Props =
    Props(new FileEventManager(config, fs, exec))
}

object FileEventManagerProtocol {

  case class WatchPath(path: Path)

  case class WatchPathResult(result: Either[FileSystemFailure, Unit])

  case object UnwatchPath

  case class UnwatchPathResult(result: Either[FileSystemFailure, Unit])

  case class FileEventResult(result: Either[FileSystemFailure, FileEvent])
}
