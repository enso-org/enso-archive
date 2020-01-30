package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Server capability to provide document formatting on typing. */
case class DocumentOnTypeFormattingOptions(
  firstTriggerCharacter: String,
  moreTriggerCharacter: Option[Seq[String]]
)

object DocumentOnTypeFormattingOptions {
  implicit val serverCapabilitiesDocumentOnTypeFormattingOptionsEncoder
    : Encoder[DocumentOnTypeFormattingOptions] =
    deriveEncoder
  implicit val serverCapabilitiesDocumentOnTypeFormattingOptionsDecoder
    : Decoder[DocumentOnTypeFormattingOptions] =
    deriveDecoder
}
