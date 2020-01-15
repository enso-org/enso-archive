package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Sync()

object Sync {
  implicit val clientCapabilitiesTextDocumentSyncDecoder: Decoder[Sync] =
    deriveDecoder
}
