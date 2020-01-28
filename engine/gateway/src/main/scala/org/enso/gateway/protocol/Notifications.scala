package org.enso.gateway.protocol

import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  DidChangeTextDocumentParams,
  DidCloseTextDocumentParams,
  DidOpenTextDocumentParams,
  DidSaveTextDocumentParams,
  VoidParams
}

/** Parent class for notification extractor objects. */
sealed abstract class NotificationExtractor[T <: Params](
  val method: String
) {
  def unapply(
    request: Notification[T]
  ): Option[Option[T]] =
    request.method match {
      case `method` =>
        Some(request.params)
      case _ => None
    }
}

/** All notifications. */
object Notifications {

  /** Sent from the client to the server after the client received the result of
    * the initialize request but before the client is sending any other request
    * or notification to the server.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialized
    */
  object Initialized extends NotificationExtractor[VoidParams]("initialized")

  /** Asks the server to exit its process.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#exit
    */
  object Exit extends NotificationExtractor[VoidParams]("exit")

  /** The notification sent from the client to the server to signal newly opened
    * text documents.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didOpen
    */
  object DidOpenTextDocument
      extends NotificationExtractor[DidOpenTextDocumentParams](
        "textDocument/didOpen"
      )

  /** The notification sent from the client to the server to signal changes to a
    * text document.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didChange
    */
  object DidChangeTextDocument
      extends NotificationExtractor[DidChangeTextDocumentParams](
        "textDocument/didChange"
      )

  /** The notification sent from the client to the server when the document was
    * saved in the client.
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didSave
    */
  object DidSaveTextDocument
      extends NotificationExtractor[DidSaveTextDocumentParams](
        "textDocument/didSave"
      )

  /** The notification sent from the client to the server when the document got
    * closed in the client.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didClose
    */
  object DidCloseTextDocument
      extends NotificationExtractor[DidCloseTextDocumentParams](
        "textDocument/didClose"
      )

}
