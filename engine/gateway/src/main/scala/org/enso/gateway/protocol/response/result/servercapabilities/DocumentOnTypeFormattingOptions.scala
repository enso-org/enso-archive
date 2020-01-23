package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/** The server provides document formatting on typing. */
case class DocumentOnTypeFormattingOptions(
  firstTriggerCharacter: String,
  moreTriggerCharacter: Option[Seq[String]]
)

object DocumentOnTypeFormattingOptions {
  implicit val serverCapabilitiesDocumentOnTypeFormattingOptionsEncoder
    : Encoder[DocumentOnTypeFormattingOptions] =
    deriveEncoder
}
