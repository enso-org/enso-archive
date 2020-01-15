package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Declaration()

object Declaration {
  implicit val clientCapabilitiesTextDocumentDeclarationDecoder
    : Decoder[Declaration] =
    deriveDecoder
}
