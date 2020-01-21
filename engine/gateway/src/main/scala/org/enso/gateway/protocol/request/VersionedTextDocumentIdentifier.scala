package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.enso.gateway.protocol.request.Params.DocumentUri

case class VersionedTextDocumentIdentifier(
  uri: DocumentUri,
  version: Option[Int] = None
)

object VersionedTextDocumentIdentifier {
  implicit val versionedTextDocumentIdentifierDecoder
    : Decoder[VersionedTextDocumentIdentifier] =
    deriveDecoder
}
