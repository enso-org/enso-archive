package org.enso.gateway.protocol

import org.enso.gateway.protocol.request.Params

/**
  * Parent trait for notifications extractor objects
  */
sealed trait Notifications {

  /**
    * Name of JSON-RPC method
    */
  val method: String

  def unapply[T <: Params](
    request: Notification[T]
  ): Option[Option[T]] =
    request.method match {
      case `method` =>
        Some(request.params)
      case _ => None
    }
}

/**
  * All notifications
  */
object Notifications {

  /**
    * The initialized notification is sent from the client to the server after the client received the result of
    * the initialize request but before the client is sending any other request or notification to the server
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialized
    */
  object Initialized extends Notifications {
    override val method = "initialized"
  }

  /**
    * A notification to ask the server to exit its process
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#exit
    */
  object Exit extends Notifications {
    override val method = "exit"
  }

  /**
    * The notification sent from the client to the server to signal newly opened text documents
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didOpen
    */
  object DidOpenTextDocument extends Notifications {
    override val method = "textDocument/didOpen"
  }

  /**
    * The notification sent from the client to the server to signal changes to a text document
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didChange
    */
  object DidChangeTextDocument extends Notifications {
    override val method = "textDocument/didChange"
  }

  /**
    * The notification sent from the client to the server when the document was saved in the client
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didSave
    */
  object DidSaveTextDocument extends Notifications {
    override val method = "textDocument/didSave"
  }

  /**
    * The notification sent from the client to the server when the document got closed in the client
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_didClose
    */
  object DidCloseTextDocument extends Notifications {
    override val method = "textDocument/didClose"
  }

}
