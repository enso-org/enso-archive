package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Definition()

object Definition {
  implicit val clientCapabilitiesTextDocumentDefinitionDecoder
    : Decoder[Definition] =
    deriveDecoder
}
