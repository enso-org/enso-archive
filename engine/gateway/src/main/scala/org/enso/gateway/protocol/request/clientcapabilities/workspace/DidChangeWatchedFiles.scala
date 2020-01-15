package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class DidChangeWatchedFiles()

object DidChangeWatchedFiles {
  implicit val clientCapabilitiesWorkspaceDidChangeWatchedFilesDecoder
    : Decoder[DidChangeWatchedFiles] =
    deriveDecoder
}
