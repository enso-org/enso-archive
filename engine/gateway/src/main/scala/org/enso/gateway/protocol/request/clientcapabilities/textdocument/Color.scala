package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Color()

object Color {
  implicit val clientCapabilitiesTextDocumentColorDecoder: Decoder[Color] =
    deriveDecoder
}
