package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DocumentFormattingProvider()

object DocumentFormattingProvider {
  implicit val serverCapabilitiesDocumentFormattingProviderEncoder
    : Encoder[DocumentFormattingProvider] =
    deriveEncoder
}
