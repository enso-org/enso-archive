package org.enso.languageserver

import akka.actor.ActorRef

trait ErrorResponse {
  def id: Id
}

object ErrorResponse {

  case class InvalidRequest(id: Id, replyTo: ActorRef) extends ErrorResponse

  case class ServerNotInitialized(id: Id, replyTo: ActorRef)
      extends ErrorResponse

}
