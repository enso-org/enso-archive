package org.enso.projectmanager.infrastructure.repo

import org.enso.projectmanager.model.ProjectMetadata
import zio.{ZEnv, ZIO}

trait ProjectRepository {

  def exists(name: String): ZIO[ZEnv, ProjectRepositoryFailure, Boolean]

  def createProject(
    project: ProjectMetadata
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit]

}
