package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `textDocument/documentColor` and the
  * `textDocument/colorPresentation` request.
  */
case class Color(
  dynamicRegistration: Option[Boolean] = None
)
object Color {
  implicit val clientCapabilitiesTextDocumentColorDecoder: Decoder[Color] =
    deriveDecoder
  implicit val clientCapabilitiesTextDocumentColorEncoder: Encoder[Color] =
    deriveEncoder
}
