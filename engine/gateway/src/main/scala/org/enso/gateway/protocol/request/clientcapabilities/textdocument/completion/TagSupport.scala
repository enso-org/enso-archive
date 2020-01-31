package org.enso.gateway.protocol.request.clientcapabilities.textdocument.completion

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Array of [[CompletionItemTag]].
  *
  * Part of [[CompletionItem]].
  */
case class TagSupport(
  valueSet: Seq[CompletionItemTag]
) extends AnyVal
object TagSupport {
  implicit val clientCapabilitiesTextDocumentCompletionTagSupportDecoder
    : Decoder[TagSupport] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentCompletionTagSupportEncoder
    : Encoder[TagSupport] = deriveEncoder
}
