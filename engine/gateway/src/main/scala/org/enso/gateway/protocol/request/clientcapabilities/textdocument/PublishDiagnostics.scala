package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class PublishDiagnostics()

object PublishDiagnostics {
  implicit val clientCapabilitiesTextDocumentPublishDiagnosticsDecoder
    : Decoder[PublishDiagnostics] =
    deriveDecoder
}
