package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Server capability to provide document link support. */
case class DocumentLinkOptions(
  workDoneProgress: Option[Boolean] = None,
  resolveProvider: Option[Boolean]  = None
)
object DocumentLinkOptions {
  implicit val serverCapabilitiesDocumentLinkOptionsEncoder
    : Encoder[DocumentLinkOptions] = deriveEncoder
  implicit val serverCapabilitiesDocumentLinkOptionsDecoder
    : Decoder[DocumentLinkOptions] = deriveDecoder
}
