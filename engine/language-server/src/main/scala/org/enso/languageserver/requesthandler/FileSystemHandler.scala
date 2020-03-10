package org.enso.languageserver.requesthandler

import akka.actor.Actor
import cats.effect.IO

import org.enso.languageserver.data.Config
import org.enso.languageserver.filemanager.{FileManagerProtocol, FileSystemApi}

class FileSystemHandler(config: Config, fs: FileSystemApi[IO]) extends Actor {

  override def receive: Receive = {
    case FileManagerProtocol.WriteFile(path, content) =>
      val result =
        for {
          rootPath <- config.findContentRoot(path.rootId)
          _        <- fs.write(path.toFile(rootPath), content).unsafeRunSync()
        } yield ()

      sender ! FileManagerProtocol.WriteFileResult(result)
  }
}
