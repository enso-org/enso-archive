package org.enso.languageserver

import akka.actor.ActorRef
import org.enso.languageserver.model.{Id, ServerInfo}

sealed trait OutcomingMessage {
  def id: Id
}

/** Language server response to [[Request]]. */
sealed trait Response extends OutcomingMessage
object Response {

  /** Language server response to [[Request.Initialize]]. */
  case class Initialize(
    id: Id,
    serverInfo: ServerInfo,
    replyTo: ActorRef
  ) extends Response

  /** Language server response to [[Request.Shutdown]]. */
  case class Shutdown(id: Id, replyTo: ActorRef) extends Response

  /** Language server response to [[Request.WillSaveTextDocumentWaitUntil]]. */
  case class WillSaveTextDocumentWaitUntil(id: Id, replyTo: ActorRef)
      extends Response

}

sealed trait RequestToClient extends OutcomingMessage

object RequestToClient {

  /** Corresponds to LSP request `workspace/applyEdit`
    *
    * Akka message sent by Language Server to Gateway.
    */
  case class ApplyWorkspaceEdit(id: Id) extends RequestToClient

}
