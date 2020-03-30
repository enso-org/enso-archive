package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.{
  CheckTimeout,
  ServerStartupFailure,
  ServerStoppageFailure
}
import org.enso.projectmanager.model.Project

trait LanguageServerService[F[+_, +_]] {

  def start(
    clientId: UUID,
    project: Project
  ): F[ServerStartupFailure, SocketData]

  def stop(
    clientId: UUID,
    projectId: UUID
  ): F[ServerStoppageFailure, Unit]

  def isRunning(projectId: UUID): F[CheckTimeout.type, Boolean]

}
