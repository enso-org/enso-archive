package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DocumentHighlightProvider()

object DocumentHighlightProvider {
  implicit val serverCapabilitiesDocumentHighlightProviderEncoder
    : Encoder[DocumentHighlightProvider] =
    deriveEncoder
}
