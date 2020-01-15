package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class TypeDefinition()

object TypeDefinition {
  implicit val clientCapabilitiesTextDocumentTypeDefinitionDecoder
    : Decoder[TypeDefinition] =
    deriveDecoder
}
