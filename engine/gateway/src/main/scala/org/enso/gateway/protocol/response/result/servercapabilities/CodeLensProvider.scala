package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class CodeLensProvider()

object CodeLensProvider {
  implicit val serverCapabilitiesCodeLensProviderEncoder
    : Encoder[CodeLensProvider] =
    deriveEncoder
}
