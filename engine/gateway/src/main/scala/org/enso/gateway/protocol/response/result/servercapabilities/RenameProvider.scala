package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class RenameProvider()

object RenameProvider {
  implicit val serverCapabilitiesRenameProviderEncoder
    : Encoder[RenameProvider] =
    deriveEncoder
}
