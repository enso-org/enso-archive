package org.enso.languageserver.protocol.binary

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.RemoteAddress
import org.enso.languageserver.http.server.ConnectionControllerFactory

/**
  * A factory of binary connection controllers.
  *
  * @param system an actor system
  */
class BinaryConnectionControllerFactory()(implicit system: ActorSystem)
    extends ConnectionControllerFactory {

  /**
    * Creates a connection controller that acts as front controller.
    *
    * @param maybeIp a client ip
    * @return actor ref
    */
  override def createController(maybeIp: Option[RemoteAddress.IP]): ActorRef = {
    system.actorOf(Props(new BinaryConnectionController(maybeIp)))
  }

}
