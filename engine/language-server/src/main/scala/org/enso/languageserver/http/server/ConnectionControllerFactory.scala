package org.enso.languageserver.http.server

import akka.actor.ActorRef
import akka.http.scaladsl.model.RemoteAddress

trait ConnectionControllerFactory {

  def createController(maybeIp: Option[RemoteAddress.IP]): ActorRef

}
