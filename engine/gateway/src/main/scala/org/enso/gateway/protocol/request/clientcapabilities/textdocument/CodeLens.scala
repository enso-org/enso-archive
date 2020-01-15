package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class CodeLens()

object CodeLens {
  implicit val clientCapabilitiesTextDocumentCodeLensDecoder
    : Decoder[CodeLens] =
    deriveDecoder
}
