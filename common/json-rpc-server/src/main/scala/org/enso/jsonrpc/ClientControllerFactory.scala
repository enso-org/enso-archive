package org.enso.jsonrpc

import java.util.UUID

import akka.actor.ActorRef

trait ClientControllerFactory {

  def createClientController(clientId: UUID): ActorRef

}
