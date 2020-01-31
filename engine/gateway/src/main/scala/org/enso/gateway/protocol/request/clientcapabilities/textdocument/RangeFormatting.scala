package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/rangeFormatting` request. */
case class RangeFormatting(
  dynamicRegistration: Option[Boolean] = None
)
object RangeFormatting {
  implicit val clientCapabilitiesTextDocumentRangeFormattingDecoder
    : Decoder[RangeFormatting] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentRangeFormattingEncoder
    : Encoder[RangeFormatting] = deriveEncoder
}
