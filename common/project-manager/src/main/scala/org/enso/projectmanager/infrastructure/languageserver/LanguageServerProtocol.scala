package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.model.Project

private[languageserver] object LanguageServerProtocol {

  case class StartServer(clientId: UUID, project: Project)

  sealed trait ServerStartupResult
  case class ServerStarted(socket: SocketData)      extends ServerStartupResult
  sealed trait ServerStartupFailure                 extends ServerStartupResult
  case class ServerBootFailed(throwable: Throwable) extends ServerStartupFailure
  case object ServerBootTimedOut                    extends ServerStartupFailure

}
