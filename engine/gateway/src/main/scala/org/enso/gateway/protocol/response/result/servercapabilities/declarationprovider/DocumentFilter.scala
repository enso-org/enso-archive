package org.enso.gateway.protocol.response.result.servercapabilities.declarationprovider

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** A document filter. */
case class DocumentFilter(
  language: Option[String] = None,
  scheme: Option[String]   = None,
  pattern: Option[String]  = None
)
object DocumentFilter {
  implicit val documentFilterEncoder: Encoder[DocumentFilter] =
    deriveEncoder
  implicit val documentFilterDecoder: Decoder[DocumentFilter] =
    deriveDecoder
}
