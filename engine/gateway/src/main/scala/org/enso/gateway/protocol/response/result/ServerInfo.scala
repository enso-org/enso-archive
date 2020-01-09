package org.enso.gateway.protocol.response.result

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
  * [[org.enso.gateway.protocol.response.Result.InitializeResult]] server info
  *
  * @param name    Name of Language Server
  * @param version Version of Language Server
  */
case class ServerInfo(
  name: String,
  version: Option[String] = None
)

object ServerInfo {
  implicit val serverInfoEncoder: Encoder[ServerInfo] = deriveEncoder
  implicit val serverInfoDecoder: Decoder[ServerInfo] = deriveDecoder
}
