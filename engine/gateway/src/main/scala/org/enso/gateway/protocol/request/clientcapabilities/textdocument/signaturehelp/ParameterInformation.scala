package org.enso.gateway.protocol.request.clientcapabilities.textdocument.signaturehelp

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Part of [[SignatureInformation]]. */
case class ParameterInformation(labelOffsetSupport: Option[Boolean] = None)
    extends AnyVal

object ParameterInformation {
  implicit val parameterInformationDecoder: Decoder[ParameterInformation] =
    deriveDecoder

  implicit val parameterInformationEncoder: Encoder[ParameterInformation] =
    deriveEncoder
}
