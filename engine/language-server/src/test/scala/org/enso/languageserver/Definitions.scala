package org.enso.languageserver

import org.enso.languageserver.model.{Id, ServerInfo}
import scala.concurrent.duration._

object Definitions {
  val name       = "Enso Language Server"
  val version    = "1.0"
  val serverInfo = ServerInfo(name, version)
  val id1        = Id.Number(1)
  val id2        = Id.Number(2)
  val id3        = Id.Number(3)
  val timeout    = 100.millis
}
