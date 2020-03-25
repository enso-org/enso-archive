package org.enso.projectmanager.infrastructure.languageserver

import akka.actor.{Actor, ActorLogging, Props}
import org.enso.languageserver.boot.{
  LanguageServerComponent,
  LanguageServerConfig
}
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerBootLoader.{
  Boot,
  FindFreeSocket,
  ServerBooted
}
import org.enso.projectmanager.infrastructure.net.Tcp

private[languageserver] class LanguageServerBootLoader(
  descriptor: ServerDescriptor
) extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    log.info(s"Booting a language server [$descriptor]")
    self ! FindFreeSocket
  }

  override def receive: Receive = {
    case FindFreeSocket =>
      log.debug("Looking for available socket to bind the language server")
      val port = Tcp.findAvailablePort(
        descriptor.networkConfig.interface,
        descriptor.networkConfig.minPort,
        descriptor.networkConfig.maxPort
      )
      log.info(
        s"Found a socket for the language server [${descriptor.networkConfig.interface}:$port]"
      )
      self ! Boot(SocketData(descriptor.networkConfig.interface, port))
    case Boot(socket) =>
      log.debug("Booting a language server")
      val config = LanguageServerConfig(
        socket.host,
        socket.port,
        descriptor.rootId,
        descriptor.root
      )
      val server = new LanguageServerComponent(config)
      server.start()
      log.info(s"Language server booted [$config].")
      context.parent ! ServerBooted(config, server)
      context.stop(self)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

private[languageserver] object LanguageServerBootLoader {

  def props(descriptor: ServerDescriptor): Props =
    Props(new LanguageServerBootLoader(descriptor))

  case object FindFreeSocket

  case class Boot(socketData: SocketData)

  case class ServerBooted(
    config: LanguageServerConfig,
    server: LanguageServerComponent
  )

}
