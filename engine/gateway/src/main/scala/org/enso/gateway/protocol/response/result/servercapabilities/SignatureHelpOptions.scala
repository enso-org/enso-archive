package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class SignatureHelpOptions()

object SignatureHelpOptions {
  implicit val serverCapabilitiesSignatureHelpOptionsEncoder
    : Encoder[SignatureHelpOptions] =
    deriveEncoder
}
