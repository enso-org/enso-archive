package org.enso.projectmanager.infrastructure.repo

import org.enso.projectmanager.model.ProjectEntity
import zio.{ZEnv, ZIO}

trait ProjectRepository {

  def exists(name: String): ZIO[ZEnv, ProjectRepositoryFailure, Boolean]

  def createProject(
    project: ProjectEntity
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit]

}
