package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DocumentOnTypeFormattingProvider()

object DocumentOnTypeFormattingProvider {
  implicit val serverCapabilitiesDocumentOnTypeFormattingProviderEncoder
    : Encoder[DocumentOnTypeFormattingProvider] =
    deriveEncoder
}
