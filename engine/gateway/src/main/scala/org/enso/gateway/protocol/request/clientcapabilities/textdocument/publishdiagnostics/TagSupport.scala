package org.enso.gateway.protocol.request.clientcapabilities.textdocument.publishdiagnostics

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Part of
  * [[org.enso.gateway.protocol.request.clientcapabilities.textdocument.PublishDiagnostics]].
  */
case class TagSupport(valueSet: Seq[DiagnosticTag]) extends AnyVal

object TagSupport {
  implicit val tagSupportDecoder: Decoder[TagSupport] =
    deriveDecoder

  implicit val tagSupportEncoder: Encoder[TagSupport] =
    deriveEncoder
}
