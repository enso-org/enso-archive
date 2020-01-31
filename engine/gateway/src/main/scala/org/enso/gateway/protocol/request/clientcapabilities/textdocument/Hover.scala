package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.clientcapabilities.textdocument.common.MarkupKind

/** Capabilities specific to the `textDocument/hover` request. */
case class Hover(
  dynamicRegistration: Option[Boolean] = None,
  contentFormat: Option[MarkupKind]    = None
)
object Hover {
  implicit val clientCapabilitiesTextDocumentHoverDecoder: Decoder[Hover] =
    deriveDecoder
  implicit val clientCapabilitiesTextDocumentHoverEncoder: Encoder[Hover] =
    deriveEncoder
}
