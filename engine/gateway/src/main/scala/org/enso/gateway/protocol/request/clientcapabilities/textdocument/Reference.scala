package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Reference()

object Reference {
  implicit val clientCapabilitiesTextDocumentReferenceDecoder
    : Decoder[Reference] =
    deriveDecoder
}
