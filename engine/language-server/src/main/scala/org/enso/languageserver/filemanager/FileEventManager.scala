package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorRef, Props}
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
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
final class FileEventManager(config: Config, exec: Exec[BlockingIO])
    extends Actor {

  import FileEventManagerProtocol._

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
    base: Path,
    watcher: FileEventWatcher,
    replyTo: ActorRef
  ): Receive = {
    case UnwatchPath(handler) =>
      val result = Try(watcher.stop()).fold(resultFailure, resultSuccess)
      sender() ! UnwatchPathResult(handler, result)
      context.become(uninitializedStage)

    case e: FileEventWatcherApi.WatcherEvent =>
      val event = FileEvent.fromWatcherEvent(root, base, e)
      replyTo ! FileEventResult(event)
  }

  private def resultSuccess[A](value: A): Either[FileSystemFailure, A] =
    Right(value)

  private def resultFailure[A](t: Throwable): Either[FileSystemFailure, A] =
    Left(GenericFileSystemFailure(t.getMessage()))
}

object FileEventManager {

  def props(config: Config, exec: Exec[BlockingIO]): Props =
    Props(new FileEventManager(config, exec))
}

object FileEventManagerProtocol {

  case class WatchPath(path: Path)

  case class WatchPathResult(result: Either[FileSystemFailure, Unit])

  case class UnwatchPath(handler: ActorRef)

  case class UnwatchPathResult(
    handler: ActorRef,
    result: Either[FileSystemFailure, Unit]
  )

  case class FileEventResult(result: FileEvent)
}
