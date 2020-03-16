package org.enso.projectmanager.protocol

import java.util.UUID

import akka.actor.{Actor, Props}

class ClientController(clientId: UUID) extends Actor {
  override def receive: Receive = ???
}

object ClientController {

  def props(clientId: UUID): Props = Props(new ClientController(clientId))

}
