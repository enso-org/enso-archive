package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.enso.gateway.protocol.request.clientcapabilities.workspace.{
  DidChangeConfiguration,
  DidChangeWatchedFiles,
  Edit,
  ExecuteCommand,
  WorkspaceSymbol
}

/**
  * Define capabilities for workspace features the client supports
  */
case class Workspace(
  applyEdit: Option[Boolean]                             = None,
  workspaceEdit: Option[Edit]                            = None,
  didChangeConfiguration: Option[DidChangeConfiguration] = None,
  didChangeWatchedFiles: Option[DidChangeWatchedFiles]   = None,
  symbol: Option[WorkspaceSymbol]                        = None,
  executeCommand: Option[ExecuteCommand]                 = None
)

object Workspace {
  implicit val clientCapabilitiesWorkspaceDecoder: Decoder[Workspace] =
    deriveDecoder
}
