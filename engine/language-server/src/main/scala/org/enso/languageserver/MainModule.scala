package org.enso.languageserver

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.stream.SystemMaterializer
import cats.effect.IO
import org.enso.languageserver.capability.CapabilityRouter
import org.enso.languageserver.text.BufferRegistry
import org.enso.languageserver.data.{Config, ContentDigest, Sha3Digest}
import org.enso.languageserver.filemanager.{FileSystem, FileSystemApi}

/**
  * A main module containing all components of th server.
  *
  * @param serverConfig a server config
  */
class MainModule(serverConfig: LanguageServerConfig) {

  lazy val languageServerConfig = Config(
    Map(serverConfig.contentRootUuid -> new File(serverConfig.contentRootPath))
  )

  lazy val fileSystem: FileSystemApi[IO] = new FileSystem[IO]

  implicit val contentDigest: ContentDigest = Sha3Digest

  implicit val system = ActorSystem()

  implicit val materializer = SystemMaterializer.get(system)

  lazy val languageServer =
    system.actorOf(
      Props(new LanguageServer(languageServerConfig, fileSystem)),
      "server"
    )

  lazy val bufferRegistry =
    system.actorOf(BufferRegistry.props(languageServer), "buffer-registry")

  lazy val capabilityRouter =
    system.actorOf(CapabilityRouter.props(bufferRegistry), "capability-router")

  lazy val server =
    new WebSocketServer(languageServer, bufferRegistry, capabilityRouter)

}
