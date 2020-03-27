package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.model.Project

object LanguageServerProtocol {

  case class StartServer(clientId: UUID, project: Project)

  sealed trait ServerStartupResult
  case class ServerStarted(socket: SocketData)      extends ServerStartupResult
  sealed trait ServerStartupFailure                 extends ServerStartupResult
  case class ServerBootFailed(throwable: Throwable) extends ServerStartupFailure
  case object ServerBootTimedOut                    extends ServerStartupFailure

  case class StopServer(clientId: UUID, projectId: UUID)
  sealed trait ServerStoppageResult
  case object ServerStopped extends ServerStoppageResult

  sealed trait ServerStoppageFailure              extends ServerStoppageResult
  case class FailureDuringStoppage(th: Throwable) extends ServerStoppageResult
  case object ServerNotRunning                    extends ServerStoppageFailure
  case object CannotDisconnectOtherClients        extends ServerStoppageFailure

}
