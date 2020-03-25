package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.StartServer
import org.enso.projectmanager.main.configuration.NetworkConfig

class LanguageServerController(networkConfig: NetworkConfig)
    extends Actor
    with ActorLogging {

  override def receive: Receive = running()

  private def running(servers: Map[UUID, ActorRef] = Map.empty): Receive = {
    case msg @ StartServer(_, project) =>
      if (servers.contains(project.id)) {
        servers(project.id).forward(msg)
      } else {
        val ref = context.actorOf(
          LanguageServerSupervisor.props(project, networkConfig)
        )
        ref.forward(msg)
        context.become(running(servers + (project.id -> ref)))
      }
  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}

object LanguageServerController {

  def props(networkConfig: NetworkConfig): Props =
    Props(new LanguageServerController(networkConfig))

}
