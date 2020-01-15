package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class DidChangeConfiguration()

object DidChangeConfiguration {
  implicit val clientCapabilitiesWorkspaceDidChangeConfigurationDecoder
    : Decoder[DidChangeConfiguration] =
    deriveDecoder
}
