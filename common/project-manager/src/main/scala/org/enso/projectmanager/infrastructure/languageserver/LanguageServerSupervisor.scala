package org.enso.projectmanager.infrastructure.languageserver

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import org.enso.languageserver.boot.LifecycleComponent.ComponentRestarted
import org.enso.languageserver.boot.{LanguageServerConfig, LifecycleComponent}
import org.enso.projectmanager.boot.configuration.SupervisionConfig
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.http.WebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerController.ServerDied
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.{
  RestartServer,
  SendHeartbeat,
  ServerUnresponsive,
  StartSupervision
}
import org.enso.projectmanager.util.UnhandledLogging

import scala.concurrent.duration._

class LanguageServerSupervisor(
  config: LanguageServerConfig,
  server: LifecycleComponent,
  supervisionConfig: SupervisionConfig,
  connectionFactor: WebSocketConnectionFactory,
  scheduler: Scheduler
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  override def preStart(): Unit = { self ! StartSupervision }

  override def receive: Receive = uninitialized

  private def uninitialized: Receive = {
    case StartSupervision =>
      val cancellable =
        scheduler.scheduleAtFixedRate(
          supervisionConfig.initialDelay,
          supervisionConfig.heartbeatInterval,
          self,
          SendHeartbeat
        )
      context.become(supervising(cancellable))
  }

  private def supervising(cancellable: Cancellable): Receive = {
    case SendHeartbeat =>
      val socket = SocketData(config.interface, config.port)
      context.actorOf(
        HeartbeatSession.props(
          socket,
          supervisionConfig.heartbeatTimeout,
          connectionFactor,
          scheduler
        )
      )

    case ServerUnresponsive =>
      log.info(s"Server is unresponsive [$config]. Restarting it...")
      cancellable.cancel()
      log.info(s"Restarting first time the server")
      server.restart() pipeTo self
      context.become(restarting())
  }

  private def restarting(restartCount: Int = 1): Receive = {
    case RestartServer =>
      log.info(s"Restarting $restartCount time the server")
      server.restart() pipeTo self

    case Failure(th) =>
      log.error(s"An error occurred during restarting the server [$config]", th)
      if (restartCount < supervisionConfig.numberOfRestarts) {
        scheduler.scheduleOnce(
          supervisionConfig.delayBetweenRetry,
          self,
          RestartServer
        )
        context.become(restarting(restartCount + 1))
      } else {
        log.error("Cannot restart language server")
        context.parent ! ServerDied
        context.stop(self)
      }

    case ComponentRestarted =>
      log.info(s"Language server restarted [$config]")
      val cancellable =
        scheduler.scheduleAtFixedRate(
          supervisionConfig.initialDelay,
          supervisionConfig.heartbeatInterval,
          self,
          SendHeartbeat
        )
      context.become(supervising(cancellable))
  }

}

object LanguageServerSupervisor {

  private case object StartSupervision

  private case object RestartServer

  case object SendHeartbeat

  case object ServerUnresponsive

  def props(
    config: LanguageServerConfig,
    server: LifecycleComponent,
    supervisionConfig: SupervisionConfig,
    connectionFactor: WebSocketConnectionFactory,
    scheduler: Scheduler
  ): Props =
    Props(
      new LanguageServerSupervisor(
        config,
        server,
        supervisionConfig,
        connectionFactor,
        scheduler
      )
    )

}
