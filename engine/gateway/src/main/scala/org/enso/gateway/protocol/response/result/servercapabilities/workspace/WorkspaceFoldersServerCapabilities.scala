package org.enso.gateway.protocol.response.result.servercapabilities.workspace

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** @see
  * [[org.enso.gateway.protocol.response.result.servercapabilities.Workspace]].
  */
case class WorkspaceFoldersServerCapabilities(
  supported: Option[Boolean]                       = None,
  changeNotifications: Option[ChangeNotifications] = None
)

object WorkspaceFoldersServerCapabilities {
  implicit val workspaceFoldersServerCapabilitiesEncoder
    : Encoder[WorkspaceFoldersServerCapabilities] =
    deriveEncoder
  implicit val workspaceFoldersServerCapabilitiesDecoder
    : Decoder[WorkspaceFoldersServerCapabilities] =
    deriveDecoder
}
