package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Link()

object Link {
  implicit val clientCapabilitiesTextDocumentLinkDecoder: Decoder[Link] =
    deriveDecoder
}
