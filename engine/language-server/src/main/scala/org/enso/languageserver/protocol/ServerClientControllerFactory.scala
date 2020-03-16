package org.enso.languageserver.protocol

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import org.enso.jsonrpc.ClientControllerFactory

class ServerClientControllerFactory(
  server: ActorRef,
  bufferRegistry: ActorRef,
  capabilityRouter: ActorRef
)(implicit system: ActorSystem)
    extends ClientControllerFactory {
  override def createClientController(clientId: UUID): ActorRef =
    system.actorOf(
      ClientController.props(clientId, server, bufferRegistry, capabilityRouter)
    )
}
