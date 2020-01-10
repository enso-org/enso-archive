package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/**
  * Define capabilities for text document features the client supports
  */
case class TextDocument()

object TextDocument {
  implicit val clientCapabilitiesTextDocumentDecoder: Decoder[TextDocument] =
    deriveDecoder
}
