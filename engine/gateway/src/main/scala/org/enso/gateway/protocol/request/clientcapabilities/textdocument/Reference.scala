package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/references` request. */
case class Reference(
  dynamicRegistration: Option[Boolean] = None
)

object Reference {
  implicit val clientCapabilitiesTextDocumentReferenceDecoder
    : Decoder[Reference] = deriveDecoder

  implicit val clientCapabilitiesTextDocumentReferenceEncoder
    : Encoder[Reference] = deriveEncoder
}
