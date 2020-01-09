package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
  * Define capabilities for text document features the client supports
  */
case class TextDocument()

object TextDocument {
  implicit val clientCapabilitiesTextDocumentEncoder: Encoder[TextDocument] =
    deriveEncoder
  implicit val clientCapabilitiesTextDocumentDecoder: Decoder[TextDocument] =
    deriveDecoder
}
