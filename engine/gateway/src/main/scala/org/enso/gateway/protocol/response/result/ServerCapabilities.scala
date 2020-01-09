package org.enso.gateway.protocol.response.result

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

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
  implicit val serverCapabilitiesDecoder: Decoder[ServerCapabilities] =
    deriveDecoder
}
