package org.enso.gateway.protocol.request.clientcapabilities.textdocument.common

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveEnumerationDecoder

sealed trait MarkupKind

object MarkupKind {

  case object plaintext extends MarkupKind

  case object markdown extends MarkupKind

  implicit val clientCapabilitiesTextDocumentCompletionMarkupKindDecoder
    : Decoder[MarkupKind] = deriveEnumerationDecoder
}
