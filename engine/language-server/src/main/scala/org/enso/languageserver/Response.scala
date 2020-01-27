package org.enso.languageserver

import akka.actor.ActorRef

sealed trait Response

/** Language server responses to [[Request]]. */
object Response {

  /** Language server response to [[Request.Initialize]]. */
  case class Initialize(
    id: Id,
    name: String,
    version: String,
    replyTo: ActorRef
  ) extends Response

  /** Language server response to [[Request.Shutdown]]. */
  case class Shutdown(id: Id, replyTo: ActorRef) extends Response

  /** Language server response to [[Request.ApplyWorkspaceEdit]]. */
  case class ApplyWorkspaceEdit(id: Id, replyTo: ActorRef) extends Response

  /** Language server response to [[Request.WillSaveTextDocumentWaitUntil]]. */
  case class WillSaveTextDocumentWaitUntil(id: Id, replyTo: ActorRef)
      extends Response

}
