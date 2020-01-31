package org.enso.gateway.protocol.request.clientcapabilities.textdocument.codeaction

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.CodeActionKind

/** Array of [[CodeActionKind]]. */
case class CodeActionKinds(valueSet: Seq[CodeActionKind]) extends AnyVal
object CodeActionKinds {
  implicit val codeActionKindsDecoder: Decoder[CodeActionKinds] =
    deriveDecoder
  implicit val codeActionKindsEncoder: Encoder[CodeActionKinds] =
    deriveEncoder
}
