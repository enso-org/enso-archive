package org.enso.gateway.protocol.response.result

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.enso.{languageserver => ls}

/** Server info in
  * [[org.enso.gateway.protocol.response.Result.InitializeResult]].
  *
  * @param name    Name of Language Server
  * @param version Version of Language Server
  */
case class ServerInfo(
  name: String,
  version: Option[String] = None
) {
  def toLsModel: ls.model.ServerInfo =
    ls.model.ServerInfo(name, version.getOrElse(""))
}
object ServerInfo {
  def fromLsModel(serverInfo: ls.model.ServerInfo): ServerInfo =
    ServerInfo(serverInfo.name, Some(serverInfo.version))

  implicit val serverInfoEncoder: Encoder[ServerInfo] = deriveEncoder
  implicit val serverInfoDecoder: Decoder[ServerInfo] = deriveDecoder
}
