package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class RangeFormatting()

object RangeFormatting {
  implicit val clientCapabilitiesTextDocumentRangeFormattingDecoder
    : Decoder[RangeFormatting] =
    deriveDecoder
}
