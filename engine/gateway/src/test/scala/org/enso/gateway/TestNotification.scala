package org.enso.gateway

import org.enso.gateway.JsonRpcController.jsonRpcVersion
import org.enso.gateway.protocol.request.Param.{
  TextDocumentIdentifier,
  TextDocumentItem,
  VersionedTextDocumentIdentifier
}
import org.enso.gateway.protocol.{Notification, Notifications}
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.request.Params.{
  DidChangeTextDocumentParams,
  DidCloseTextDocumentParams,
  DidOpenTextDocumentParams,
  DidSaveTextDocumentParams,
  VoidParams
}

trait TestNotification[P <: Params] {
  def notification: Notification[P]
}

object TestNotification {

  object Initialized extends TestNotification[VoidParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.Initialized.method,
      params  = Some(VoidParams())
    )
  }

  object Exit extends TestNotification[VoidParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.Exit.method,
      params  = Some(VoidParams())
    )
  }

  object DidOpenTextDocument
      extends TestNotification[DidOpenTextDocumentParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.DidOpenTextDocument.method,
      params = Some(
        DidOpenTextDocumentParams(
          textDocument = TextDocumentItem(
            uri        = "/path/to/file",
            languageId = "Enso",
            version    = 2,
            text       = "Some text"
          )
        )
      )
    )
  }

  object DidChangeTextDocument
      extends TestNotification[DidChangeTextDocumentParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.DidChangeTextDocument.method,
      params = Some(
        DidChangeTextDocumentParams(
          textDocument = VersionedTextDocumentIdentifier(
            uri = "/path/to/file"
          ),
          contentChanges = Seq()
        )
      )
    )
  }

  object DidSaveTextDocument
      extends TestNotification[DidSaveTextDocumentParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.DidSaveTextDocument.method,
      params = Some(
        DidSaveTextDocumentParams(
          textDocument = TextDocumentIdentifier(
            uri = "/path/to/file"
          )
        )
      )
    )
  }

  object DidCloseTextDocument
      extends TestNotification[DidCloseTextDocumentParams] {
    val notification = Notification(
      jsonrpc = jsonRpcVersion,
      method  = Notifications.DidCloseTextDocument.method,
      params = Some(
        DidCloseTextDocumentParams(
          textDocument = TextDocumentIdentifier(
            uri = "/path/to/file"
          )
        )
      )
    )
  }

}
