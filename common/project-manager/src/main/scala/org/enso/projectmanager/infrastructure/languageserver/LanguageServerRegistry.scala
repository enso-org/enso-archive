package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import org.enso.projectmanager.boot.configuration.{
  BootloaderConfig,
  NetworkConfig
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.{
  CheckIfServerIsRunning,
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

  private def running(
    serverControllers: Map[UUID, ActorRef] = Map.empty
  ): Receive = {
    case msg @ StartServer(_, project) =>
      if (serverControllers.contains(project.id)) {
        serverControllers(project.id).forward(msg)
      } else {
        val controller = context.actorOf(
          LanguageServerController
            .props(project, networkConfig, bootloaderConfig)
        )
        context.watch(controller)
        controller.forward(msg)
        context.become(running(serverControllers + (project.id -> controller)))
      }

    case msg @ StopServer(_, projectId) =>
      if (serverControllers.contains(projectId)) {
        serverControllers(projectId).forward(msg)
      } else {
        sender() ! ServerNotRunning
      }

    case ServerShutDown(projectId) =>
      context.become(running(serverControllers - projectId))

    case Terminated(ref) =>
      context.become(running(serverControllers.filterNot(_._2 == ref)))

    case CheckIfServerIsRunning(projectId) =>
      sender() ! serverControllers.contains(projectId)

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
