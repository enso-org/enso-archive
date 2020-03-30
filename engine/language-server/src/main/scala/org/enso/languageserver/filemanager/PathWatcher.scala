package org.enso.languageserver.filemanager

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import cats.implicits._
import org.enso.languageserver.capability.CapabilityProtocol.{
  CapabilityAcquired,
  CapabilityAcquisitionFileSystemFailure,
  CapabilityForceReleased,
  CapabilityReleased
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  Config,
  ReceivesTreeUpdates
}
import org.enso.languageserver.effect._
import org.enso.languageserver.event.ClientDisconnected
import zio._
import zio.blocking.effectBlocking

/**
  * Event manager starts [[FileEventWatcher]], handles errors, converts and
  * sends events to the client.
  *
  * @param config configuration
  * @param fs file system
  * @param exec executor of file system effects
  */
final class PathWatcher(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor
    with ActorLogging {

  import context.dispatcher, PathWatcherProtocol._

  private val restartCounter =
    new PathWatcher.RestartCounter(config.fileEventManager.maxRestarts)
  private var fileWatcher: Option[FileEventWatcher] = None

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classOf[ClientDisconnected]): Unit
  }
  override def postStop(): Unit = {
    stopWatcher(): Unit
  }

  override def receive: Receive = uninitializedStage

  private def uninitializedStage: Receive = {
    case WatchPath(path, clients) =>
      val pathToWatchResult = config
        .findContentRoot(path.rootId)
        .map(path.toFile(_))
      val result: BlockingIO[FileSystemFailure, Unit] =
        for {
          pathToWatch <- IO.fromEither(pathToWatchResult)
          _           <- validatePath(pathToWatch)
          watcher     <- IO.fromEither(buildWatcher(pathToWatch))
          _           <- IO.fromEither(startWatcher(watcher))
        } yield ()

      exec
        .exec(result)
        .map {
          case Right(()) => CapabilityAcquired
          case Left(err) => CapabilityAcquisitionFileSystemFailure(err)
        }
        .pipeTo(sender())

      pathToWatchResult match {
        case Right(root) =>
          context.become(initializedStage(root, path, clients))
        case Left(_) =>
          context.stop(self)
      }
  }

  private def initializedStage(
    root: File,
    base: Path,
    clients: Set[ActorRef]
  ): Receive = {
    case WatchPath(_, newClients) =>
      sender() ! CapabilityAcquired
      context.become(initializedStage(root, base, clients ++ newClients))

    case UnwatchPath(client) =>
      sender() ! CapabilityReleased
      unregisterClient(root, base, clients - client)

    case ClientDisconnected(client) if clients.contains(client.actor) =>
      unregisterClient(root, base, clients - client.actor)

    case e: FileEventWatcher.WatcherEvent =>
      restartCounter.reset()
      val event = FileEvent.fromWatcherEvent(root, base, e)
      clients.foreach(_ ! FileEventResult(event))

    case FileEventWatcher.WatcherError(e) =>
      stopWatcher()
      restartCounter.inc()
      if (restartCounter.canRestart) {
        log.error(s"Restart on error#${restartCounter.count}", e)
        context.system.scheduler.scheduleOnce(
          config.fileEventManager.restartTimeout,
          self,
          WatchPath(base, clients)
        )
      } else {
        log.error("Hit maximum number of restarts", e)
        clients.foreach { client =>
          client ! CapabilityForceReleased(
            CapabilityRegistration(ReceivesTreeUpdates(base))
          )
        }
      }
      context.stop(self)
  }

  private def unregisterClient(
    root: File,
    base: Path,
    clients: Set[ActorRef]
  ): Unit = {
    if (clients.isEmpty) {
      context.stop(self)
    } else {
      context.become(initializedStage(root, base, clients))
    }
  }

  private def validatePath(path: File): BlockingIO[FileSystemFailure, Unit] =
    for {
      pathExists <- fs.exists(path)
      _          <- ZIO.when(!pathExists)(IO.fail(FileNotFound))
    } yield ()

  private def buildWatcher(
    path: File
  ): Either[FileSystemFailure, FileEventWatcher] =
    Either
      .catchNonFatal(FileEventWatcher.build(path.toPath, self ! _, self ! _))
      .leftMap(errorHandler)

  private def startWatcher(
    watcher: FileEventWatcher
  ): Either[FileSystemFailure, Unit] =
    Either
      .catchNonFatal {
        fileWatcher = Some(watcher)
        exec.exec_(effectBlocking(watcher.start()))
      }
      .leftMap(errorHandler)

  private def stopWatcher(): Either[FileSystemFailure, Unit] =
    Either
      .catchNonFatal(fileWatcher.foreach(_.stop()))
      .leftMap(errorHandler)

  private val errorHandler: Throwable => FileSystemFailure = {
    case ex => GenericFileSystemFailure(ex.getMessage)
  }
}

object PathWatcher {

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
    * Creates a configuration object used to create a [[PathWatcher]].
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
    Props(new PathWatcher(config, fs, exec))
}
