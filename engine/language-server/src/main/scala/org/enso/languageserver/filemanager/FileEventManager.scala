package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import zio._
import zio.blocking.effectBlocking

import scala.util.Try

/**
  * Event manager starts [[FileEventWatcher]], handles errors, converts and
  * sends [[FileEvent]]'s to [[FileEventRegistry]].
  *
  * @param config configuration
  * @param exec executor of file system effects
  */
final class FileEventManager(config: Config, fs: FileSystemApi[BlockingIO], exec: Exec[BlockingIO])
    extends Actor {

  import context.dispatcher, FileEventManagerProtocol._

  private var fileWatcher: Option[FileEventWatcher] = None

  override def postStop(): Unit = {
    // cleanup resources
    Try(fileWatcher.foreach(_.stop())): Unit
  }

  override def receive: Receive = uninitializedStage

  private def uninitializedStage: Receive = {
    case WatchPath(path) =>
      val pathToWatchResult = config
        .findContentRoot(path.rootId)
        .map(path.toFile(_))
      val result: BlockingIO[FileSystemFailure, Unit] =
        for {
          pathToWatch <- IO.fromEither(pathToWatchResult)
          _ <- validatePath(pathToWatch)
          watcher <- buildWatcher(pathToWatch)
          _ <- startWatcher(watcher)
        } yield ()

      exec
        .exec(result)
        .map(WatchPathResult(_))
        .pipeTo(sender())
      pathToWatchResult.foreach { root =>
        context.become(initializedStage(root, path, sender()))
      }

    case UnwatchPath =>
      // nothing to cleanup, just reply
      sender() ! UnwatchPathResult(Right(()))
  }

  private def initializedStage(
    root: File,
    base: Path,
    replyTo: ActorRef
  ): Receive = {
    case UnwatchPath =>
      exec
        .exec(stopWatcher)
        .map(UnwatchPathResult(_))
        .pipeTo(replyTo)
      context.become(uninitializedStage)

    case e: FileEventWatcher.WatcherEvent =>
      val event = FileEvent.fromWatcherEvent(root, base, e)
      replyTo ! FileEventResult(event)
  }

  private def buildWatcher(path: File): IO[FileSystemFailure, FileEventWatcher] =
    IO(FileEventWatcher.build(path.toPath, self ! _, self ! _))
      .mapError(errorHandler)

  private def validatePath(path: File): BlockingIO[FileSystemFailure, Unit] =
    for {
      pathExists <- fs.exists(path)
      _ <- ZIO.when(!pathExists)(IO.fail(FileNotFound))
    } yield ()

  private def startWatcher(watcher: FileEventWatcher): IO[FileSystemFailure, Unit] =
    IO {
      this.fileWatcher = Some(watcher)
      exec.exec_(effectBlocking(watcher.start()))
    }.mapError(errorHandler)

  private def stopWatcher: IO[FileSystemFailure, Unit] =
    IO {
      this.fileWatcher.foreach(_.stop())
    }.mapError(errorHandler)

  private val errorHandler: Throwable => FileSystemFailure = {
    case ex => GenericFileSystemFailure(ex.getMessage)
  }
}

object FileEventManager {

  /**
    * Creates a configuration object used to create a [[FileEventManager]].
    *
    * @param config configuration
    * @param fs file system
    * @param exec executor of file system effects
    */
  def props(config: Config, fs: FileSystemApi[BlockingIO], exec: Exec[BlockingIO]): Props =
    Props(new FileEventManager(config, fs, exec))
}
