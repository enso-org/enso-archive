package org.enso.languageserver

import akka.actor.ActorRef

sealed trait RequestOrNotification

sealed trait Request extends RequestOrNotification {
  def id: Id

  def replyTo: ActorRef
}

/** Akka messages sent by Gateway received LSP requests. */
object Request {

  /** Akka message sent by Gateway received LSP request `initialize`. */
  case class Initialize(
    id: Id,
    dynamicRegistration: Boolean = false,
    willSaveWaitUntil: Boolean   = false,
    didSave: Boolean             = false,
    replyTo: ActorRef
  ) extends Request

  /** Akka message sent by Gateway received LSP request `shutdown`. */
  case class Shutdown(id: Id, replyTo: ActorRef) extends Request

  /** Akka message sent by Gateway received LSP request `workspace/applyEdit`.
    */
  case class ApplyWorkspaceEdit(id: Id, replyTo: ActorRef) extends Request

  /** Akka message sent by Gateway received LSP request `textDocument/willSave`.
    */
  case class WillSaveTextDocumentWaitUntil(id: Id, replyTo: ActorRef)
      extends Request

}

sealed trait Notification extends RequestOrNotification

/** Akka messages sent by Gateway received LSP notifications. */
object Notification {

  /** Akka message sent by Gateway received LSP notification `initialized`. */
  case object Initialized extends Notification

  /** Akka message sent by Gateway received LSP notification `exit`. */
  case object Exit extends Notification

  /** Akka message sent by Gateway received LSP notification
    * `textDocument/didOpen`.
    */
  case object DidOpenTextDocument extends Notification

  /** Akka message sent by Gateway received LSP notification
    * `textDocument/didChange`.
    */
  case object DidChangeTextDocument extends Notification

  /** Akka message sent by Gateway received LSP notification
    * `textDocument/didSave`.
    */
  case object DidSaveTextDocument extends Notification

  /** Akka message sent by Gateway received LSP notification
    * `textDocument/didClose`.
    */
  case object DidCloseTextDocument extends Notification

}
