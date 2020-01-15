package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class TextDocumentSync()

object TextDocumentSync {
  implicit val serverCapabilitiesTextDocumentSyncEncoder
    : Encoder[TextDocumentSync] =
    deriveEncoder
}
