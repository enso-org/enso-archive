package org.enso.projectmanager.service

import java.util.UUID

import org.enso.projectmanager.infrastructure.log.Logging
import org.enso.projectmanager.infrastructure.repo.ProjectRepositoryFailure.{
  CannotLoadIndex,
  InconsistentStorage,
  ProjectNotFoundInIndex,
  StorageFailure
}
import org.enso.projectmanager.infrastructure.repo.{
  ProjectRepository,
  ProjectRepositoryFailure
}
import org.enso.projectmanager.infrastructure.time.Clock
import org.enso.projectmanager.model.Project
import org.enso.projectmanager.service.ProjectServiceFailure.{
  DataStoreFailure,
  ProjectExists,
  ProjectNotFound
}
import org.enso.projectmanager.service.ValidationFailure.{
  EmptyName,
  NameContainsForbiddenCharacter
}
import zio.{IO, ZEnv, ZIO}

class ProjectService(
  validator: ProjectValidator[IO],
  repo: ProjectRepository[ZIO[ZEnv, *, *]],
  log: Logging,
  clock: Clock
) extends ProjectServiceApi[ZIO[ZEnv, *, *]] {

  override def createUserProject(
    name: String
  ): ZIO[ZEnv, ProjectServiceFailure, UUID] = {
    // format: off
    for {
      _            <- log.debug(s"Creating project $name.")
      _            <- validateName(name)
      _            <- validateExists(name)
      creationTime <- clock.nowInUtc()
      projectId     = UUID.randomUUID()
      project       = Project(projectId, name, creationTime)
      _            <- repo.createUserProject(project).mapError(toServiceFailure)
      _            <- log.info(s"Project $project created.")
    } yield projectId
    // format: on
  }

  override def deleteUserProject(
    projectId: UUID
  ): ZIO[ZEnv, ProjectServiceFailure, Unit] =
    log.debug(s"Deleting project $projectId.") *>
    repo.deleteUserProject(projectId).mapError(toServiceFailure) *>
    log.info(s"Project $projectId deleted.")

  private def validateExists(
    name: String
  ): ZIO[ZEnv, ProjectServiceFailure, Unit] =
    repo
      .exists(name)
      .mapError(toServiceFailure)
      .flatMap { exists =>
        if (exists) ZIO.fail(ProjectExists)
        else ZIO.unit
      }

  private val toServiceFailure
    : ProjectRepositoryFailure => ProjectServiceFailure = {
    case CannotLoadIndex(msg) =>
      DataStoreFailure(s"Cannot load project index [$msg]")
    case StorageFailure(msg) =>
      DataStoreFailure(s"Storage failure [$msg]")
    case ProjectNotFoundInIndex =>
      ProjectNotFound
    case InconsistentStorage(msg) =>
      DataStoreFailure(s"Project repository inconsistency detected [$msg]")
  }

  private def validateName(
    name: String
  ): ZIO[ZEnv, ProjectServiceFailure, Unit] =
    validator
      .validateName(name)
      .mapError {
        case EmptyName =>
          ProjectServiceFailure.ValidationFailure(
            "Cannot create project with empty name"
          )
        case NameContainsForbiddenCharacter(chars) =>
          ProjectServiceFailure.ValidationFailure(
            s"Project name contains forbidden characters: ${chars.mkString(",")}"
          )
      }

}
