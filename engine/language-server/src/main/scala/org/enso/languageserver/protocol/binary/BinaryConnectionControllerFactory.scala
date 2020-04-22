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

  /** @inheritdoc **/
  override def createController(maybeIp: Option[RemoteAddress.IP]): ActorRef = {
    system.actorOf(Props(new BinaryConnectionController(maybeIp)))
  }

}
