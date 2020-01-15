package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class CompletionOptions()

object CompletionOptions {
  implicit val serverCapabilitiesCompletionOptionsEncoder
    : Encoder[CompletionOptions] =
    deriveEncoder
}
