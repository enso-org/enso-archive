package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/foldingRange` request. */
case class FoldingRange(
  dynamicRegistration: Option[Boolean] = None,
  rangeLimit: Option[Int]              = None,
  lineFoldingOnly: Option[Boolean]     = None
)

object FoldingRange {
  implicit val clientCapabilitiesTextDocumentFoldingRangeDecoder
    : Decoder[FoldingRange] = deriveDecoder

  implicit val clientCapabilitiesTextDocumentFoldingRangeEncoder
    : Encoder[FoldingRange] = deriveEncoder
}
