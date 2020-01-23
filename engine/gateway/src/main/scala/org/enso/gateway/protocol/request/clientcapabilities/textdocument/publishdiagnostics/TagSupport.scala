package org.enso.gateway.protocol.request.clientcapabilities.textdocument.publishdiagnostics

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class TagSupport(valueSet: Seq[DiagnosticTag]) extends AnyVal

object TagSupport {
  implicit val tagSupportDecoder: Decoder[TagSupport] =
    deriveDecoder
}
