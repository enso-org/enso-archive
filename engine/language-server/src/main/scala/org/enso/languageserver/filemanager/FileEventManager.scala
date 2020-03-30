package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.pipe
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import zio._
import zio.blocking.effectBlocking

import scala.util.Try

/**
  * Event manager starts [[FileEventWatcher]], handles errors, converts and
  * sends events to the client.
  *
  * @param config configuration
  * @param fs file system
  * @param exec executor of file system effects
  */
final class FileEventManager(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor
    with ActorLogging {

  import context.dispatcher, FileEventManagerProtocol._

  private val restartCounter =
    new FileEventManager.RestartCounter(config.fileEventManager.maxRestarts)
  private var fileWatcher: Option[FileEventWatcher] = None

  override def postStop(): Unit = {
    // cleanup resources
    Try(fileWatcher.foreach(_.stop())): Unit
  }

  override def receive: Receive = uninitializedStage

  private def uninitializedStage: Receive = {
    case WatchPath(path, client) =>
      val pathToWatchResult = config
        .findContentRoot(path.rootId)
        .map(path.toFile(_))
      val result: BlockingIO[FileSystemFailure, Unit] =
        for {
          pathToWatch <- IO.fromEither(pathToWatchResult)
          _           <- validatePath(pathToWatch)
          watcher     <- buildWatcher(pathToWatch)
          _           <- startWatcher(watcher)
        } yield ()

      exec
        .exec(result)
        .map(WatchPathResult(_))
        .pipeTo(sender())
      pathToWatchResult.foreach { root =>
        context.become(initializedStage(root, path, sender(), client))
      }

    case UnwatchPath =>
      // nothing to cleanup, just reply
      sender() ! UnwatchPathResult(Right(()))
  }

  private def initializedStage(
    root: File,
    base: Path,
    subscriber: ActorRef,
    client: ActorRef
  ): Receive = {
    case UnwatchPath =>
      exec
        .exec(stopWatcher)
        .map(UnwatchPathResult(_))
        .pipeTo(subscriber)
      context.become(uninitializedStage)

    case e: FileEventWatcher.WatcherEvent =>
      restartCounter.reset()
      val event = FileEvent.fromWatcherEvent(root, base, e)
      client ! FileEventResult(event)

    case FileEventWatcher.WatcherError(e) =>
      Try(fileWatcher.foreach(_.stop()))
      restartCounter.inc()
      if (restartCounter.canRestart) {
        log.error(s"Restart on error#${restartCounter.count}", e)
        context.system.scheduler.scheduleOnce(
          config.fileEventManager.restartTimeout,
          self,
          WatchPath(base, client)
        )
      } else {
        log.error("Hit maximum number of restarts", e)
        subscriber ! FileEventError(e)
        self ! PoisonPill
      }
      context.become(uninitializedStage)

  }

  private def buildWatcher(
    path: File
  ): IO[FileSystemFailure, FileEventWatcher] =
    IO(FileEventWatcher.build(path.toPath, self ! _, self ! _))
      .mapError(errorHandler)

  private def validatePath(path: File): BlockingIO[FileSystemFailure, Unit] =
    for {
      pathExists <- fs.exists(path)
      _          <- ZIO.when(!pathExists)(IO.fail(FileNotFound))
    } yield ()

  private def startWatcher(
    watcher: FileEventWatcher
  ): IO[FileSystemFailure, Unit] =
    IO {
      fileWatcher = Some(watcher)
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
    * Conunt unsuccessful file watcher restarts
    *
    * @param maxRestarts maximum number of restarts we can try
    */
  final private class RestartCounter(maxRestarts: Int) {

    private var restartCount: Int = 0

    /**
      * Return current restart count.
      */
    def count: Int =
      restartCount

    /**
      * Increment restart count.
      */
    def inc(): Unit =
      restartCount += 1

    /**
      * Reset restart count.
      */
    def reset(): Unit =
      restartCount = 0

    /**
      * Return true if we hit the maximum number of restarts.
      */
    def canRestart: Boolean =
      restartCount < maxRestarts
  }

  /**
    * Creates a configuration object used to create a [[FileEventManager]].
    *
    * @param config configuration
    * @param fs file system
    * @param exec executor of file system effects
    */
  def props(
    config: Config,
    fs: FileSystemApi[BlockingIO],
    exec: Exec[BlockingIO]
  ): Props =
    Props(new FileEventManager(config, fs, exec))
}
