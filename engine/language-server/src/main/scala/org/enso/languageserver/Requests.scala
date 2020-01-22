package org.enso.languageserver

import akka.actor.ActorRef

object Requests {

  /** Akka message sent by Gateway received LSP request `initialize`. */
  case class Initialize(
    id: Id,
    willSaveWaitUntil: Boolean,
    replyTo: ActorRef
  )

  /** Language server response to [[]]. */
  case class Shutdown(id: Id, replyTo: ActorRef)

  /** Language server response to [[]]. */
  case class ApplyWorkspaceEdit(id: Id, replyTo: ActorRef)

  /** Language server response to [[]]. */
  case class WillSaveTextDocumentWaitUntil(id: Id, replyTo: ActorRef)

}
