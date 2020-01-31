package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/formatting` request. */
case class Formatting(
  dynamicRegistration: Option[Boolean] = None
)
object Formatting {
  implicit val clientCapabilitiesTextDocumentFormattingDecoder
    : Decoder[Formatting] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentFormattingEncoder
    : Encoder[Formatting] = deriveEncoder
}
