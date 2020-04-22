package org.enso.languageserver.http.server

import akka.actor.ActorRef
import akka.http.scaladsl.model.RemoteAddress

/**
  * A factory of connection controllers.
  */
trait ConnectionControllerFactory {

  /**
    * Creates a connection controller that acts as front controller.
    *
    * @param clientIp a client ip
    * @return actor ref
    */
  def createController(clientIp: RemoteAddress.IP): ActorRef

}
