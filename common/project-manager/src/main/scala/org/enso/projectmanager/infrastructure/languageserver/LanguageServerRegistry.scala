package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.projectmanager.boot.configuration.{
  BootloaderConfig,
  NetworkConfig
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.{
  ServerNotRunning,
  StartServer,
  StopServer
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerRegistry.ServerShutDown

class LanguageServerRegistry(
  networkConfig: NetworkConfig,
  bootloaderConfig: BootloaderConfig
) extends Actor
    with ActorLogging {

  override def receive: Receive = running()

  private def running(servers: Map[UUID, ActorRef] = Map.empty): Receive = {
    case msg @ StartServer(_, project) =>
      if (servers.contains(project.id)) {
        servers(project.id).forward(msg)
      } else {
        val ref = context.actorOf(
          LanguageServerSupervisor
            .props(project, networkConfig, bootloaderConfig)
        )
        ref.forward(msg)
        context.become(running(servers + (project.id -> ref)))
      }

    case msg @ StopServer(_, projectId) =>
      if (servers.contains(projectId)) {
        servers(projectId).forward(msg)
      } else {
        sender() ! ServerNotRunning
      }

    case ServerShutDown(projectId) =>
      context.become(running(servers - projectId))

  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

object LanguageServerRegistry {

  case class ServerShutDown(projectId: UUID)

  def props(
    networkConfig: NetworkConfig,
    bootloaderConfig: BootloaderConfig
  ): Props =
    Props(new LanguageServerRegistry(networkConfig, bootloaderConfig))

}
