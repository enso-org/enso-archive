package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Stash}
import org.enso.languageserver.boot.{
  LanguageServerComponent,
  LanguageServerConfig
}
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerBootLoader.ServerBooted
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.StartServer
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.{
  Boot,
  BootTimeout
}
import org.enso.projectmanager.main.configuration.NetworkConfig
import org.enso.projectmanager.model.Project

import scala.concurrent.duration._

private[languageserver] class LanguageServerSupervisor(
  project: Project,
  networkConfig: NetworkConfig
) extends Actor
    with Stash
    with ActorLogging {

  import context.dispatcher

  private val descriptor =
    ServerDescriptor(
      name          = s"language-server-${project.id}",
      rootId        = UUID.randomUUID(),
      root          = project.path.get,
      networkConfig = networkConfig
    )

  override def preStart(): Unit = {
    self ! Boot
  }

  override def receive: Receive = {
    case Boot =>
      val bootloader =
        context.actorOf(LanguageServerBootLoader.props(descriptor))
      val timeoutCancellable =
        context.system.scheduler.scheduleOnce(30.seconds, self, BootTimeout)
      context.become(booting(timeoutCancellable))

    case _ => stash()
  }

  private def booting(
    timeoutCancellable: Cancellable
  ): Receive = {
    case BootTimeout =>
      log.error(s"Booting failed for $descriptor")
      context.stop(self)

    case ServerBooted(config, server) =>
      unstashAll()
      context.become(supervising(config, server))

    case _ => stash()
  }

  private def supervising(
    config: LanguageServerConfig,
    server: LanguageServerComponent,
    clients: Set[UUID] = Set.empty
  ): Receive = {
    case StartServer(clientId, _) =>
      sender() ! LanguageServerProtocol.ServerStarted(
        SocketData(config.interface, config.port)
      )
      context.become(supervising(config, server, clients + clientId))
  }

  private def stopping(): Receive = ???

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

object LanguageServerSupervisor {

  def props(project: Project, networkConfig: NetworkConfig): Props =
    Props(new LanguageServerSupervisor(project, networkConfig))

  case object BootTimeout

  case object Boot

}
