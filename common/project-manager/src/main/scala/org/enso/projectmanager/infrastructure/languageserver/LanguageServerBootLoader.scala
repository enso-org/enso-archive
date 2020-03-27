package org.enso.projectmanager.infrastructure.languageserver

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import org.enso.languageserver.boot.{
  LanguageServerComponent,
  LanguageServerConfig
}
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerBootLoader.{
  Boot,
  FindFreeSocket,
  ServerBootFailed,
  ServerBooted
}
import org.enso.projectmanager.infrastructure.net.Tcp

private[languageserver] class LanguageServerBootLoader(
  descriptor: LanguageServerDescriptor
) extends Actor
    with ActorLogging {

  import context.dispatcher

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
      self ! Boot
      context.become(
        booting(SocketData(descriptor.networkConfig.interface, port))
      )
  }

  private def booting(socket: SocketData, retry: Int = 0): Receive = {
    case Boot =>
      log.debug("Booting a language server")
      val config = LanguageServerConfig(
        socket.host,
        socket.port,
        descriptor.rootId,
        descriptor.root,
        descriptor.name,
        context.dispatcher
      )
      val server = new LanguageServerComponent(config)
      server.start().map(_ => config -> server) pipeTo self

    case Failure(th) =>
      log.error(
        th,
        s"An error occurred during boot of Language Server [${descriptor.name}]"
      )
      if (retry < 10) {
        self ! Boot
        context.become(booting(socket, retry + 1))
      } else {
        log.error("Tried 10 times to boot Language Server. Giving up.")
        context.parent ! ServerBootFailed(th)
        context.stop(self)
      }

    case (config: LanguageServerConfig, server: LanguageServerComponent) =>
      log.info(s"Language server booted [$config].")
      context.parent ! ServerBooted(config, server)
      context.stop(self)

  }

  override def unhandled(message: Any): Unit =
    log.warning("Boot Received unknown message: {}", message)

}

private[languageserver] object LanguageServerBootLoader {

  def props(descriptor: LanguageServerDescriptor): Props =
    Props(new LanguageServerBootLoader(descriptor))

  case object FindFreeSocket

  case object Boot

  case class ServerBootFailed(th: Throwable)

  case class ServerBooted(
    config: LanguageServerConfig,
    server: LanguageServerComponent
  )

}
