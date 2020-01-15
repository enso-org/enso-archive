package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class HoverProvider()

object HoverProvider {
  implicit val serverCapabilitiesHoverProviderEncoder: Encoder[HoverProvider] =
    deriveEncoder
}
