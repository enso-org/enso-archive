package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/** The server provides execute command support. */
case class ExecuteCommandOptions(
  workDoneProgress: Option[Boolean] = None,
  commands: Seq[String]
)
object ExecuteCommandOptions {
  implicit val serverCapabilitiesExecuteCommandOptionsEncoder
    : Encoder[ExecuteCommandOptions] =
    deriveEncoder
}
