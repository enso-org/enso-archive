package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import zio.blocking.effectBlocking

import scala.util.Try

/**
  * Event manager starts [[FileEventWatcher]], handles errors, converts and
  * sends [[FileEvent]]'s to [[FileEventRegistry]].
  *
  * @param config configuration
  * @param exec executor of file system effects
  */
final class FileEventManager(config: Config, exec: Exec[BlockingIO])
    extends Actor with ActorLogging {

  import FileEventManagerProtocol._

  private var fileWatcher: FileEventWatcher = _

  override def postStop(): Unit = {
    // cleanup resources
    if (fileWatcher ne null) {
      Try(fileWatcher.stop()): Unit
    }
  }

  override def receive: Receive = uninitializedStage

  private def uninitializedStage: Receive = {
    case WatchPath(path) =>
      config.findContentRoot(path.rootId) match {
        case Right(rootPath) =>
          val pathToWatch = path.toFile(rootPath).toPath
          val watcherResult =
            Try(FileEventWatcher.build(pathToWatch, self ! _, self ! _))
              .fold(resultFailure, resultSuccess)
              .map { watcher =>
                fileWatcher = watcher
                exec.exec_(effectBlocking(watcher.start()))
              }
          sender() ! WatchPathResult(watcherResult)
          context.become(initializedStage(rootPath, path, sender()))

        case Left(err) =>
          sender() ! WatchPathResult(Left(err))
      }
  }

  private def initializedStage(
    root: File,
    base: Path,
    replyTo: ActorRef
  ): Receive = {
    case UnwatchPath =>
      val result = Try(fileWatcher.stop()).fold(resultFailure, resultSuccess)
      sender() ! UnwatchPathResult(result)
      context.become(uninitializedStage)

    case e: FileEventWatcher.WatcherEvent =>
      val event = FileEvent.fromWatcherEvent(root, base, e)
      replyTo ! FileEventResult(event)

    case FileEventWatcher.WatcherError(e) =>
      log.error("FileEventWatcher error", e)
  }

  private def resultSuccess[A](value: A): Either[FileSystemFailure, A] =
    Right(value)

  private def resultFailure[A](t: Throwable): Either[FileSystemFailure, A] =
    Left(GenericFileSystemFailure(t.getMessage()))
}

object FileEventManager {

  /**
    * Creates a configuration object used to create a [[FileEventManager]].
    *
    * @param config configuration
    * @param exec executor of file system effects
    */
  def props(config: Config, exec: Exec[BlockingIO]): Props =
    Props(new FileEventManager(config, exec))
}
