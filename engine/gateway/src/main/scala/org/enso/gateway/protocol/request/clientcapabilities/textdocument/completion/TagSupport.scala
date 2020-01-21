package org.enso.gateway.protocol.request.clientcapabilities.textdocument.completion

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class TagSupport(
  valueSet: Seq[CompletionItemTag]
)

object TagSupport {
  implicit val clientCapabilitiesTextDocumentCompletionTagSupportDecoder
    : Decoder[TagSupport] = deriveDecoder
}
