package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class DocumentSymbol()

object DocumentSymbol {
  implicit val clientCapabilitiesTextDocumentSymbolDecoder
    : Decoder[DocumentSymbol] =
    deriveDecoder
}
