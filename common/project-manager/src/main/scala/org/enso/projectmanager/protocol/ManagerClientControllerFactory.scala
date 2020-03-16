package org.enso.projectmanager.protocol

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import org.enso.jsonrpc.ClientControllerFactory

class ManagerClientControllerFactory(system: ActorSystem)
    extends ClientControllerFactory {

  override def createClientController(clientId: UUID): ActorRef =
    system.actorOf(ClientController.props(clientId))

}
