package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/onTypeFormatting` request. */
case class OnTypeFormatting(
  dynamicRegistration: Option[Boolean] = None
)
object OnTypeFormatting {
  implicit val clientCapabilitiesTextDocumentOnTypeFormattingDecoder
    : Decoder[OnTypeFormatting] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentOnTypeFormattingEncoder
    : Encoder[OnTypeFormatting] = deriveEncoder
}
