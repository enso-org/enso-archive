package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/** The server provides document link support. */
case class DocumentLinkOptions(
  workDoneProgress: Option[Boolean] = None,
  resolveProvider: Option[Boolean]  = None
)

object DocumentLinkOptions {
  implicit val serverCapabilitiesDocumentLinkOptionsEncoder
    : Encoder[DocumentLinkOptions] =
    deriveEncoder
}
