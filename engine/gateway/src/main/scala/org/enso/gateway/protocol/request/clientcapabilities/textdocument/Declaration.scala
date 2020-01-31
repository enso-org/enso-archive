package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/declaration` request. */
case class Declaration(
  dynamicRegistration: Option[Boolean] = None,
  linkSupport: Option[Boolean]         = None
)
object Declaration {
  implicit val clientCapabilitiesTextDocumentDeclarationDecoder
    : Decoder[Declaration] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentDeclarationEncoder
    : Encoder[Declaration] = deriveEncoder
}
