package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, Props}
import org.enso.languageserver.data.Config
import org.enso.languageserver.requesthandler.RequestTimeout
import zio._

class FileSystemManager(
  config: Config,
  fs: FileSystem,
  runtime: Runtime[zio.ZEnv]
) extends Actor
    with ActorLogging {

  override def receive: Receive = {
    case FileManagerProtocol.WriteFile(path, content) =>
      val write =
        for {
          rootPath <- ZIO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.write(path.toFile(rootPath), content)
        } yield ()

      runAsync(write)({
        case Some(result) =>
          sender ! FileManagerProtocol.WriteFileResult(result)
        case None =>
          sender ! RequestTimeout
      })
  }

  private def runAsync[A](
    io: FileSystem.BlockingIO[A]
  )(onComplete: Option[Either[FileSystemFailure, A]] => Unit): Unit = {
    val duration = zio.duration.Duration.fromScala(config.timeouts.io)
    runtime.unsafeRunAsync(io.timeout(duration))({ exit =>
      val resultOpt =
        exit.fold(p => Some(Left(fromCause(p))), _.map(Right(_)))
      onComplete(resultOpt)
    })
  }

  private def fromCause(cause: Cause[FileSystemFailure]): FileSystemFailure =
    cause.failureOption match {
      case Some(failure) => failure
      case None =>
        cause.defects.foreach { t =>
          log.error("Failure during FileSystem operation:", t)
        }
        GenericFileSystemFailure(cause.defects.mkString(","))
    }

}

object FileSystemManager {

  def props(
    config: Config,
    fs: FileSystem,
    runtime: Runtime[zio.ZEnv] = zio.Runtime.default
  ): Props = Props(new FileSystemManager(config, fs, runtime))

}
