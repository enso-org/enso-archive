package org.enso.gateway.protocol

import org.enso.gateway.protocol.request.Params.ApplyWorkspaceEditParams

object RequestsToClient {
  private val applyWorkspaceEdit = "workspace/applyEdit"

  /** The request sent from the server to the client to modify resource on the
    * client side.
    *
    * LSP Spec:
    * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#workspace_applyEdit
    */
  def ApplyWorkspaceEdit(
    id: Id,
    params: Option[ApplyWorkspaceEditParams] = None
  ): Request[ApplyWorkspaceEditParams] = Request(
    jsonrpc = JsonRpcController.jsonRpcVersion,
    id      = id,
    method  = applyWorkspaceEdit,
    params  = params
  )
}
