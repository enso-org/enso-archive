package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, Props}
import akka.routing.SmallestMailboxPool
import akka.pattern.pipe
import org.enso.languageserver.ZioExec
import org.enso.languageserver.data.Config
import zio._
import zio.blocking.blocking

class FileManager(config: Config, fs: FileSystem)
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
        .execTimed(config.fileManager.timeout, blocking(write))
        .map(FileManagerProtocol.WriteFileResult)
        .pipeTo(sender())
  }
}

object FileManager {

  def props(
    config: Config,
    fs: FileSystem
  ): Props =
    SmallestMailboxPool(config.fileManager.parallelism)
      .props(Props(new FileManager(config, fs)))

}
