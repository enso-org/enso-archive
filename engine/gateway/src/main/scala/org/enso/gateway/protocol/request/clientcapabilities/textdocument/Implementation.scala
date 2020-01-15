package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Implementation()

object Implementation {
  implicit val clientCapabilitiesTextDocumentImplementationDecoder
    : Decoder[Implementation] =
    deriveDecoder
}
