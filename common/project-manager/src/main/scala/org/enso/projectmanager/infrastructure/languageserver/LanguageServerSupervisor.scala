package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.Status.Failure
import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  Cancellable,
  OneForOneStrategy,
  Props,
  Stash,
  SupervisorStrategy,
  Terminated
}
import org.enso.languageserver.boot.{
  LanguageServerComponent,
  LanguageServerConfig
}
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerBootLoader.{
  ServerBootFailed,
  ServerBooted
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.{
  CannotDisconnectOtherClients,
  FailureDuringStoppage,
  ServerStarted,
  StartServer,
  StopServer
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.{
  Boot,
  BootTimeout
}
import org.enso.projectmanager.boot.configuration.NetworkConfig
import org.enso.projectmanager.model.Project
import akka.pattern.pipe
import org.enso.languageserver.boot.LanguageServerComponent.ServerStopped
import org.enso.projectmanager.event.ClientDisconnected
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerRegistry.ServerShutDown

import scala.concurrent.duration._

private[languageserver] class LanguageServerSupervisor(
  project: Project,
  networkConfig: NetworkConfig
) extends Actor
    with Stash
    with ActorLogging {

  import context.dispatcher

  private val descriptor =
    LanguageServerDescriptor(
      name          = s"language-server-${project.id}",
      rootId        = UUID.randomUUID(),
      root          = project.path.get,
      networkConfig = networkConfig
    )

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(10) {
    case _ => SupervisorStrategy.Restart
  }

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ClientDisconnected])
    self ! Boot
  }

  override def receive: Receive = {
    case Boot =>
      val bootloader =
        context.actorOf(LanguageServerBootLoader.props(descriptor))
      context.watch(bootloader)
      val timeoutCancellable =
        context.system.scheduler.scheduleOnce(30.seconds, self, BootTimeout)
      context.become(booting(bootloader, timeoutCancellable))

    case _ => stash()
  }

  private def booting(
    Bootloader: ActorRef,
    timeoutCancellable: Cancellable
  ): Receive = {
    case BootTimeout =>
      log.error(s"Booting failed for $descriptor")
      stop()

    case ServerBootFailed(th) =>
      unstashAll()
      timeoutCancellable.cancel()
      context.become(bootFailed(th))

    case ServerBooted(config, server) =>
      unstashAll()
      timeoutCancellable.cancel()
      context.become(supervising(config, server))

    case Terminated(Bootloader) =>
      log.error(s"Bootloader for project ${project.name} failed")
      unstashAll()
      timeoutCancellable.cancel()
      context.become(
        bootFailed(new Exception("The number of boot retries exceeded"))
      )

    case _ => stash()
  }

  private def supervising(
    config: LanguageServerConfig,
    server: LanguageServerComponent,
    clients: Set[UUID] = Set.empty
  ): Receive = {
    case StartServer(clientId, _) =>
      sender() ! ServerStarted(
        SocketData(config.interface, config.port)
      )
      context.become(supervising(config, server, clients + clientId))

    case Terminated(_) =>
      log.debug(s"Bootloader for $project terminated.")

    case StopServer(clientId, _) =>
      removeClient(config, server, clients, clientId, Some(sender()))

    case ClientDisconnected(clientId) =>
      removeClient(config, server, clients, clientId, None)

  }

  private def removeClient(
    config: LanguageServerConfig,
    server: LanguageServerComponent,
    clients: Set[UUID],
    clientId: UUID,
    maybeRequester: Option[ActorRef]
  ): Unit = {
    val updatedClients = clients - clientId
    if (updatedClients.isEmpty) {
      server.stop() pipeTo self
      context.become(stopping(maybeRequester))
    } else {
      sender() ! CannotDisconnectOtherClients
      context.become(supervising(config, server, updatedClients))
    }
  }

  private def bootFailed(th: Throwable): Receive = {
    case StartServer(_, _) =>
      sender() ! LanguageServerProtocol.ServerBootFailed(th)
      stop()

  }

  private def stopping(maybeRequester: Option[ActorRef]): Receive = {
    case Failure(th) =>
      log.error(
        th,
        s"An error occurred during Language server shutdown [$project]."
      )
      maybeRequester.foreach(_ ! FailureDuringStoppage(th))
      stop()

    case ServerStopped =>
      log.info(s"Language server shutdown successfully [$project].")
      maybeRequester.foreach(_ ! LanguageServerProtocol.ServerStopped)
      stop()
  }

  private def stop(): Unit = {
    context.stop(self)
    context.parent ! ServerShutDown(project.id)
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

object LanguageServerSupervisor {

  def props(project: Project, networkConfig: NetworkConfig): Props =
    Props(new LanguageServerSupervisor(project, networkConfig))

  case object BootTimeout

  case object Boot

}
