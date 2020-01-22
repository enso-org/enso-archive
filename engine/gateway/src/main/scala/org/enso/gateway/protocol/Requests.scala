package org.enso.gateway.protocol

import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  ApplyWorkspaceEditParams,
  InitializeParams,
  VoidParams,
  WillSaveTextDocumentWaitUntilParams
}

/** Parent trait for request extractor objects. */
sealed abstract class RequestExtractor[T <: Params](
  val method: String
) {
  def unapply(request: Request[T]): Option[(Id, Option[T])] =
    request.method match {
      case `method` =>
        Some((request.id, request.params))
      case _ => None
    }
}

/** All requests. */
object Requests {

  /** The request sent as the first request from the client to the server.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#initialize
    */
  object Initialize extends RequestExtractor[InitializeParams]("initialize")

  /** The request is sent from the client to the server. It asks the server to
    * shut down, but to not exit (otherwise the response might not be
    * delivered correctly to the client).
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#shutdown
    */
  object Shutdown extends RequestExtractor[VoidParams]("shutdown")

  /** The request sent from the server to the client to modify resource on the
    * client side.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#workspace_applyEdit
    */
  object ApplyWorkspaceEdit
      extends RequestExtractor[ApplyWorkspaceEditParams]("workspace/applyEdit")

  /** The request sent from the client to the server before the document is
    * actually saved.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#textDocument_willSaveWaitUntil
    */
  object WillSaveTextDocumentWaitUntil
      extends RequestExtractor[WillSaveTextDocumentWaitUntilParams](
        "textDocument/willSaveWaitUntil"
      )
}
