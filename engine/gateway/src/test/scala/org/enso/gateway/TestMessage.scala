package org.enso.gateway

import org.enso.gateway.JsonRpcController.jsonRpcVersion
import org.enso.gateway.protocol.request.Param.{
  ClientCapabilities,
  ClientInfo,
  TextDocumentIdentifier,
  TextDocumentSaveReason,
  WorkspaceEdit
}
import org.enso.gateway.protocol.{Request, Requests, Response}
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  ApplyWorkspaceEditParams,
  InitializeParams,
  VoidParams,
  WillSaveTextDocumentWaitUntilParams
}
import org.enso.gateway.protocol.response.Result.{
  ApplyWorkspaceEditResult,
  InitializeResult,
  NullResult,
  WillSaveTextDocumentWaitUntilResult
}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import TestMessageDefinitions._
import org.enso.gateway.protocol.response.result.servercapabilities.TextDocumentSync.WillSaveWaitUntil

trait TestMessage[P <: Params] {
  def request: Request[P]

  def response: Response
}

object TestMessage {

  object Initialize extends TestMessage[InitializeParams] {
    val request = Request(
      jsonrpc = jsonRpcVersion,
      id      = id1,
      method  = Requests.Initialize.method,
      params = Some(
        InitializeParams(
          clientInfo = Some(
            ClientInfo(
              name    = clientName,
              version = Some(clientVersion)
            )
          ),
          capabilities = ClientCapabilities()
        )
      )
    )

    val response = Response.result(
      id = Some(id1),
      result = InitializeResult(
        capabilities = ServerCapabilities(
          textDocumentSync = Some(
            WillSaveWaitUntil(
              willSaveWaitUntil = true
            )
          )
        ),
        serverInfo = Some(
          ServerInfo(
            name    = serverName,
            version = Some(serverVersion)
          )
        )
      )
    )
  }

  object Shutdown extends TestMessage[VoidParams] {
    val request = Request(
      jsonrpc = jsonRpcVersion,
      id      = id2,
      method  = Requests.Shutdown.method,
      params  = Some(VoidParams())
    )

    val response = Response.result(
      id     = Some(id2),
      result = NullResult
    )
  }

  object ApplyWorkspaceEdit extends TestMessage[ApplyWorkspaceEditParams] {
    val request = Request(
      jsonrpc = jsonRpcVersion,
      id      = id2,
      method  = Requests.ApplyWorkspaceEdit.method,
      params = Some(
        ApplyWorkspaceEditParams(
          edit = WorkspaceEdit()
        )
      )
    )

    val response: Response = Response.result(
      id = Some(id2),
      result = ApplyWorkspaceEditResult(
        applied = false
      )
    )
  }

  object WillSaveTextDocumentWaitUntil
      extends TestMessage[WillSaveTextDocumentWaitUntilParams] {
    val request = Request(
      jsonrpc = jsonRpcVersion,
      id      = id2,
      method  = Requests.WillSaveTextDocumentWaitUntil.method,
      params = Some(
        WillSaveTextDocumentWaitUntilParams(
          textDocument = TextDocumentIdentifier(
            uri = "/path/to/HelloWorld.enso"
          ),
          reason = TextDocumentSaveReason.Manual
        )
      )
    )

    val response: Response = Response.result(
      id     = Some(id2),
      result = WillSaveTextDocumentWaitUntilResult()
    )
  }

}
