package org.enso.gateway.protocol.request.clientcapabilities.textdocument.completion

import io.circe.Decoder

sealed abstract class CompletionItemTag(value: Int)

object CompletionItemTag {

  object Deprecated extends CompletionItemTag(1)

  implicit val textDocumentCompletionItemTagDecoder
    : Decoder[CompletionItemTag] =
    Decoder.decodeInt.emap {
      case 1 => Right(Deprecated)
      case _ => Left("Invalid CompletionItemTag")
    }
}
