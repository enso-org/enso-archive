package org.enso.runner

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import cats.effect.IO
import org.enso.languageserver.data.Config
import org.enso.languageserver.filemanager.FileSystem
import org.enso.languageserver.{
  LanguageProtocol,
  LanguageServer,
  WebSocketServer
}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object LanguageServerApp {

  def run(langServConfig: LanguageServerConfig): Unit = {
    println("Starting Language Server...")
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val config = Config(
      Map(
        langServConfig.contentRootUuid -> new File(
          langServConfig.contentRootPath
        )
      )
    )
    val languageServer =
      system.actorOf(Props(new LanguageServer(config, new FileSystem[IO])))

    languageServer ! LanguageProtocol.Initialize

    val server = new WebSocketServer(languageServer)

    val binding = Await.result(
      server.bind(langServConfig.interface, langServConfig.port),
      3.seconds
    )
    println(
      s"Started server at ${langServConfig.interface}:${langServConfig.port}, press enter to kill server"
    )
    StdIn.readLine()
  }

}
