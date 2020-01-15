package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DeclarationProvider()

object DeclarationProvider {
  implicit val serverCapabilitiesDeclarationProviderEncoder
    : Encoder[DeclarationProvider] =
    deriveEncoder
}
