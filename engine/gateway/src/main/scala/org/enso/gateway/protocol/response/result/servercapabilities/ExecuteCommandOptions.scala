package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class ExecuteCommandOptions()

object ExecuteCommandOptions {
  implicit val serverCapabilitiesExecuteCommandOptionsEncoder
    : Encoder[ExecuteCommandOptions] =
    deriveEncoder
}
