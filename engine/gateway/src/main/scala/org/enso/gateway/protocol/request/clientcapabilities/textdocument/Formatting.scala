package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Formatting()

object Formatting {
  implicit val clientCapabilitiesTextDocumentFormattingDecoder
    : Decoder[Formatting] =
    deriveDecoder
}
