package org.enso.projectmanager.service

import java.util.UUID

import org.enso.projectmanager.infrastructure.log.Logging
import org.enso.projectmanager.infrastructure.repo.ProjectRepositoryFailure.{
  CannotLoadMetadata,
  StorageFailure
}
import org.enso.projectmanager.infrastructure.repo.{
  ProjectRepository,
  ProjectRepositoryFailure
}
import org.enso.projectmanager.infrastructure.time.Clock
import org.enso.projectmanager.model.ProjectEntity
import org.enso.projectmanager.service.CreateProjectFailure.{
  DataStoreFailure,
  ProjectExists
}
import org.enso.projectmanager.service.ValidationFailure.{
  EmptyName,
  NameContainsForbiddenCharacter
}
import zio.{ZEnv, ZIO}

class ProjectService(
  validator: ProjectValidatorApi,
  repo: ProjectRepository,
  log: Logging,
  clock: Clock
) extends ProjectServiceApi {

  override def createProject(
    name: String
  ): ZIO[ZEnv, CreateProjectFailure, Unit] = {
    // format: off
    for {
      _            <- log.debug(s"Creating project $name.")
      _            <- validateName(name)
      _            <- validateExists(name)
      creationTime <- clock.nowInUtc()
      projectId     = UUID.randomUUID()
      project       = ProjectEntity(projectId, name, creationTime, None)
      _            <- repo.createProject(project).mapError(toServiceFailure)
      _            <- log.info(s"Project $project created.")
    } yield ()
    // format: on
  }

  private def validateExists(
    name: String
  ): ZIO[ZEnv, CreateProjectFailure, Unit] =
    repo
      .exists(name)
      .mapError(toServiceFailure)
      .flatMap { exists =>
        if (exists) ZIO.fail(ProjectExists)
        else ZIO.unit
      }

  private val toServiceFailure
    : ProjectRepositoryFailure => CreateProjectFailure = {
    case CannotLoadMetadata(msg) => DataStoreFailure(msg)
    case StorageFailure(msg)     => DataStoreFailure(msg)
  }

  private def validateName(
    name: String
  ): ZIO[ZEnv, CreateProjectFailure, Unit] =
    validator
      .validateName(name)
      .mapError {
        case EmptyName =>
          CreateProjectFailure.ValidationFailure(
            "Cannot create project with empty name"
          )
        case NameContainsForbiddenCharacter(char) =>
          CreateProjectFailure.ValidationFailure(
            "Forbidden characters in the project name"
          )
      }

}
