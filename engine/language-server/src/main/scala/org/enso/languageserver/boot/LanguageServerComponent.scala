package org.enso.languageserver.boot

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import org.enso.languageserver.LanguageProtocol

import scala.concurrent.Await
import scala.concurrent.duration._

class LanguageServerComponent(config: LanguageServerConfig) {

  private var maybeServerState: Option[(MainModule, Http.ServerBinding)] = None

  def start(): Unit = {
    println("Starting Language Server...")
    val mainModule = new MainModule(config)

    mainModule.languageServer ! LanguageProtocol.Initialize

    val binding: Http.ServerBinding =
      Await.result(
        mainModule.server.bind(config.interface, config.port),
        3.seconds
      )

    println(
      s"Started server at ${config.interface}:${config.port}, press enter to kill server"
    )
    maybeServerState = Some(mainModule, binding)
  }

  def stop(): Unit = {
    maybeServerState foreach {
      case (mainModule, binding) =>
        binding.terminate(10.seconds)
        mainModule.system.terminate()
    }
  }

}
