package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class ImplementationProvider()

object ImplementationProvider {
  implicit val serverCapabilitiesImplementationProviderEncoder
    : Encoder[ImplementationProvider] =
    deriveEncoder
}
