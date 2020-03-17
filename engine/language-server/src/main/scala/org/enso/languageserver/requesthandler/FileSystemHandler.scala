package org.enso.languageserver.requesthandler

import akka.actor.{Actor, ActorLogging, Props}
import zio._

import org.enso.languageserver.data.Config
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  FileSystem,
  FileSystemFailure,
  GenericFileSystemFailure
}

import scala.concurrent.duration._

class FileSystemHandler(
  config: Config,
  fs: FileSystem,
  runtime: Runtime[zio.ZEnv],
  timeout: FiniteDuration
) extends Actor
    with ActorLogging {

  override def receive: Receive = {
    case FileManagerProtocol.WriteFile(path, content) =>
      val write =
        for {
          rootPath <- ZIO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.write(path.toFile(rootPath), content)
        } yield ()

      runIO(write, timeout).foreach { result =>
        sender ! FileManagerProtocol.WriteFileResult(result)
      }
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

  private def runIO[A](
    io: FileSystem.BlockingIO[A],
    timeout: Duration
  ): Option[Either[FileSystemFailure, A]] =
    runtime
      .unsafeRunSync(io.timeout(zio.duration.Duration.fromScala(timeout)))
      .fold(p => Some(Left(fromCause(p))), _.map(Right(_)))
}

object FileSystemHandler {

  def props(
    config: Config,
    fs: FileSystem,
    runtime: Runtime[zio.ZEnv] = zio.Runtime.default,
    timeout: FiniteDuration = 3.seconds
  ): Props = Props(new FileSystemHandler(config, fs, runtime, timeout))

}
