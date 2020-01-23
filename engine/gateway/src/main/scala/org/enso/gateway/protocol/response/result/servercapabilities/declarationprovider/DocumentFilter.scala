package org.enso.gateway.protocol.response.result.servercapabilities.declarationprovider

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class DocumentFilter(
  language: Option[String] = None,
  scheme: Option[String]   = None,
  pattern: Option[String]  = None
)

object DocumentFilter {
  implicit val documentFilterEncoder: Encoder[DocumentFilter] =
    deriveEncoder
}
