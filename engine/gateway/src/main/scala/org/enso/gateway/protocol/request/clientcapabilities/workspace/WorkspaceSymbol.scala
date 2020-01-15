package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class WorkspaceSymbol()

object WorkspaceSymbol {
  implicit val clientCapabilitiesWorkspaceWorkspaceSymbolDecoder
    : Decoder[WorkspaceSymbol] =
    deriveDecoder
}
