package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
  * Define capabilities for workspace features the client supports
  */
case class Workspace(
  applyEdit: Option[Boolean] = None
)

object Workspace {
  implicit val clientCapabilitiesWorkspaceEncoder: Encoder[Workspace] =
    deriveEncoder
  implicit val clientCapabilitiesWorkspaceDecoder: Decoder[Workspace] =
    deriveDecoder
}
