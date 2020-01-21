package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Synchronization capabilities. */
case class Sync(
  dynamicRegistration: Option[Boolean],
  willSave: Option[Boolean],
  willSaveWaitUntil: Option[Boolean],
  didSave: Option[Boolean]
)
object Sync {
  implicit val clientCapabilitiesTextDocumentSyncDecoder: Decoder[Sync] =
    deriveDecoder
}
