package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class CodeActionProvider()

object CodeActionProvider {
  implicit val serverCapabilitiesCodeActionProviderEncoder
    : Encoder[CodeActionProvider] =
    deriveEncoder
}
