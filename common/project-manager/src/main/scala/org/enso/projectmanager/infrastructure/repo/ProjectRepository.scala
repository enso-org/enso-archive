package org.enso.projectmanager.infrastructure.repo

import java.util.UUID

import org.enso.projectmanager.model.ProjectMetadata

trait ProjectRepository[F[_, _]] {

  def exists(name: String): F[ProjectRepositoryFailure, Boolean]

  def createUserProject(
    project: ProjectMetadata
  ): F[ProjectRepositoryFailure, Unit]

  def deleteUserProject(projectId: UUID): F[ProjectRepositoryFailure, Unit]

}
