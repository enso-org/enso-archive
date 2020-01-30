package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/implementation` request. */
case class Implementation(
  dynamicRegistration: Option[Boolean] = None,
  linkSupport: Option[Boolean]         = None
)

object Implementation {
  implicit val clientCapabilitiesTextDocumentImplementationDecoder
    : Decoder[Implementation] =
    deriveDecoder

  implicit val clientCapabilitiesTextDocumentImplementationEncoder
    : Encoder[Implementation] =
    deriveEncoder
}
