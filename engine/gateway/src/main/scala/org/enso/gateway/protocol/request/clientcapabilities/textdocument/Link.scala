package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/documentLink` request. */
case class Link(
  dynamicRegistration: Option[Boolean] = None,
  tooltipSupport: Option[Boolean]      = None
)

object Link {
  implicit val clientCapabilitiesTextDocumentLinkDecoder: Decoder[Link] =
    deriveDecoder

  implicit val clientCapabilitiesTextDocumentLinkEncoder: Encoder[Link] =
    deriveEncoder
}
