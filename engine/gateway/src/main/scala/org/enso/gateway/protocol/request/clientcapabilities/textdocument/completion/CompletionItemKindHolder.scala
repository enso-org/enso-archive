package org.enso.gateway.protocol.request.clientcapabilities.textdocument.completion

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class CompletionItemKindHolder(
  valueSet: Option[Seq[CompletionItemKind]] = None
)

object CompletionItemKindHolder {
  implicit val clientCapabilitiesTextDocumentCompletionItemKindHolderDecoder
    : Decoder[CompletionItemKindHolder] =
    deriveDecoder
}
