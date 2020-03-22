package org.enso.projectmanager.infrastructure.repository

import java.util.UUID

import org.enso.projectmanager.model.Project

trait ProjectRepository[F[_, _]] {

  def exists(name: String): F[ProjectRepositoryFailure, Boolean]

  def createUserProject(
    project: Project
  ): F[ProjectRepositoryFailure, Unit]

  def deleteUserProject(projectId: UUID): F[ProjectRepositoryFailure, Unit]

}
