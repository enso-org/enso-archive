package org.enso.gateway.protocol

import org.enso.gateway.protocol.request.Params

/**
  * Parent trait for requests extractor objects
  */
sealed trait Requests {

  /**
    * Name of JSON-RPC method
    */
  val method: String

  def unapply[T <: Params](
    request: Request[T]
  ): Option[(Id, Option[T])] =
    request.method match {
      case `method` =>
        Some((request.id, request.params))
      case _ => None
    }
}

/**
  * All requests
  */
object Requests {

  /**
    * The initialize request is sent as the first request from the client to the server
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialize
    */
  object Initialize extends Requests {
    override val method = "initialize"
  }

  /**
    * The shutdown request is sent from the client to the server. It asks the server to shut down,
    * but to not exit (otherwise the response might not be delivered correctly to the client)
    *
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#shutdown
    */
  object Shutdown extends Requests {
    override val method = "shutdown"
  }

  /**
    * The request sent from the server to the client to modify resource on the client side
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#workspace_applyEdit
    */
  object ApplyWorkspaceEdit extends Requests {
    override val method = "workspace/applyEdit"
  }

  /**
    * The request sent from the client to the server before the document is actually saved
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_willSaveWaitUntil
    */
  object WillSaveTextDocumentWaitUntil extends Requests {
    override val method = "textDocument/willSaveWaitUntil"
  }

}
