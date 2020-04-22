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
    * @param maybeIp a client ip
    * @return actor ref
    */
  def createController(maybeIp: Option[RemoteAddress.IP]): ActorRef

}
