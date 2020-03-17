package org.enso.languageserver.filemanager

import akka.pattern.pipe
import akka.actor.{Actor, ActorLogging, Props}
import org.enso.languageserver.ZioExec
import org.enso.languageserver.data.Config
import zio._

class FileSystemManager(config: Config, fs: FileSystem)
    extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = {
    case FileManagerProtocol.WriteFile(path, content) =>
      val write =
        for {
          rootPath <- ZIO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.write(path.toFile(rootPath), content)
        } yield ()

      ZioExec()
        .execTimed(config.timeouts.io, write)
        .pipeTo(sender())
  }
}

object FileSystemManager {

  def props(
    config: Config,
    fs: FileSystem
  ): Props = Props(new FileSystemManager(config, fs))

}
