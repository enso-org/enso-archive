package org.enso.gateway.protocol.request.clientcapabilities.textdocument.codeaction

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class CodeActionLiteralSupport(codeActionKind: CodeActionKinds)

object CodeActionLiteralSupport {
  implicit val codeActionLiteralSupportDecoder
    : Decoder[CodeActionLiteralSupport] =
    deriveDecoder
}
