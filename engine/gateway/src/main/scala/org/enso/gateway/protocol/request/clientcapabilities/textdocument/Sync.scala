package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Synchronization capabilities. */
case class Sync(
  dynamicRegistration: Option[Boolean] = None,
  willSave: Option[Boolean]            = None,
  willSaveWaitUntil: Option[Boolean]   = None,
  didSave: Option[Boolean]             = None
)
object Sync {
  implicit val clientCapabilitiesTextDocumentSyncDecoder: Decoder[Sync] =
    deriveDecoder
  implicit val clientCapabilitiesTextDocumentSyncEncoder: Encoder[Sync] =
    deriveEncoder
}
