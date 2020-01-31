package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/typeDefinition` request. */
case class TypeDefinition(
  dynamicRegistration: Option[Boolean] = None,
  linkSupport: Option[Boolean]         = None
)
object TypeDefinition {
  implicit val clientCapabilitiesTextDocumentTypeDefinitionDecoder
    : Decoder[TypeDefinition] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentTypeDefinitionEncoder
    : Encoder[TypeDefinition] = deriveEncoder
}
