package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Server capability to provide signature help support. */
case class SignatureHelpOptions(
  triggerCharacters: Option[Seq[String]]   = None,
  retriggerCharacters: Option[Seq[String]] = None,
  workDoneProgress: Option[Boolean]        = None
)
object SignatureHelpOptions {
  implicit val serverCapabilitiesSignatureHelpOptionsEncoder
    : Encoder[SignatureHelpOptions] =
    deriveEncoder
  implicit val serverCapabilitiesSignatureHelpOptionsDecoder
    : Decoder[SignatureHelpOptions] =
    deriveDecoder
}
