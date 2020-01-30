package org.enso.gateway.protocol.request.clientcapabilities.textdocument.signaturehelp

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.clientcapabilities.textdocument.common.MarkupKind

/** Part of
  * [[org.enso.gateway.protocol.request.clientcapabilities.textdocument.SignatureHelp]].
  */
case class SignatureInformation(
  documentationFormat: Option[Seq[MarkupKind]]       = None,
  parameterInformation: Option[ParameterInformation] = None
)

object SignatureInformation {
  implicit val signatureInformationDecoder: Decoder[SignatureInformation] =
    deriveDecoder

  implicit val signatureInformationEncoder: Encoder[SignatureInformation] =
    deriveEncoder
}
