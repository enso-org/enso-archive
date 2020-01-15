package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Rename()

object Rename {
  implicit val clientCapabilitiesTextDocumentRenameDecoder: Decoder[Rename] =
    deriveDecoder
}
