package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/** The server provides code lens. */
case class CodeLensOptions(
  workDoneProgress: Option[Boolean] = None,
  resolveProvider: Option[Boolean]  = None
)

object CodeLensOptions {
  implicit val serverCapabilitiesCodeLensOptionsEncoder
    : Encoder[CodeLensOptions] =
    deriveEncoder
}
