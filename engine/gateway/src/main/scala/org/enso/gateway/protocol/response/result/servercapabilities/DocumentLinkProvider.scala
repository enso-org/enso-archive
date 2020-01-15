package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DocumentLinkProvider()

object DocumentLinkProvider {
  implicit val serverCapabilitiesDocumentLinkProviderEncoder
    : Encoder[DocumentLinkProvider] =
    deriveEncoder
}
