package org.enso.languageserver

import akka.actor.ActorRef

object RequestReceived {

  /** Language server response to [[Initialize]]. */
  case class Initialize(id: Id, replyTo: ActorRef)

  /** */
  case class Shutdown(id: Id, replyTo: ActorRef)

  /** */
  case class ApplyWorkspaceEdit(id: Id, replyTo: ActorRef)

  /** */
  case class WillSaveTextDocumentWaitUntil(id: Id, replyTo: ActorRef)

}
