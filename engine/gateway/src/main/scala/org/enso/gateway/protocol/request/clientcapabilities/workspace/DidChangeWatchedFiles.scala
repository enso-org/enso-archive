package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Capabilities specific to the `workspace/didChangeWatchedFiles` notification.
  */
case class DidChangeWatchedFiles(dynamicRegistration: Option[Boolean] = None)
object DidChangeWatchedFiles {
  implicit val clientCapabilitiesWorkspaceDidChangeWatchedFilesDecoder
    : Decoder[DidChangeWatchedFiles] = deriveDecoder
  implicit val clientCapabilitiesWorkspaceDidChangeWatchedFilesEncoder
    : Encoder[DidChangeWatchedFiles] = deriveEncoder
}
