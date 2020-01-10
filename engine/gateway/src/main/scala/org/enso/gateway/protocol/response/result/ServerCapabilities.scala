package org.enso.gateway.protocol.response.result

import io.circe.generic.semiauto.deriveEncoder
import io.circe.Encoder

/**
  * [[org.enso.gateway.protocol.response.Result.InitializeResult]] server capabilities
  */
case class ServerCapabilities(
  workspaceSymbolProvider: Option[Boolean]              = None,
  workspace: Option[servercapabilities.Workspace]       = None,
  experimental: Option[servercapabilities.Experimental] = None
)

object ServerCapabilities {
  implicit val serverCapabilitiesEncoder: Encoder[ServerCapabilities] =
    deriveEncoder
}
