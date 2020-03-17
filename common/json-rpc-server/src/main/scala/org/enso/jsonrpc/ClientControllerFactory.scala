package org.enso.jsonrpc

import java.util.UUID

import akka.actor.ActorRef

/**
  * Classes implementing this trait are responsible for creating client
  * controllers upon a new connection.
  */
trait ClientControllerFactory {

  /**
    * Creates a client controller actor.
    *
    * @param clientId a client identifier
    * @return
    */
  def createClientController(clientId: UUID): ActorRef

}
