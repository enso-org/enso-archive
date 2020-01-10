package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/**
  * Define capabilities for workspace features the client supports
  */
case class Workspace(
  applyEdit: Option[Boolean] = None
)

object Workspace {
  implicit val clientCapabilitiesWorkspaceDecoder: Decoder[Workspace] =
    deriveDecoder
}
