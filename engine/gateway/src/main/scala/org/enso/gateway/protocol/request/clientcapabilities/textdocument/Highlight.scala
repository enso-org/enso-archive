package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/documentHighlight` request. */
case class Highlight(
  dynamicRegistration: Option[Boolean] = None
)
object Highlight {
  implicit val clientCapabilitiesTextDocumentHighlightDecoder
    : Decoder[Highlight] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentHighlightEncoder
    : Encoder[Highlight] = deriveEncoder
}
