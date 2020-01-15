package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class ExecuteCommand()

object ExecuteCommand {
  implicit val clientCapabilitiesWorkspaceExecuteCommandDecoder
    : Decoder[ExecuteCommand] =
    deriveDecoder
}
