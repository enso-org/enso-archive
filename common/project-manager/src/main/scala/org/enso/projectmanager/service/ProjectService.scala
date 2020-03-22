package org.enso.projectmanager.service

import java.util.UUID

import org.enso.projectmanager.infrastructure.log.Logging
import org.enso.projectmanager.infrastructure.random.Generator
import org.enso.projectmanager.infrastructure.repository.ProjectRepositoryFailure.{
  CannotLoadIndex,
  InconsistentStorage,
  ProjectNotFoundInIndex,
  StorageFailure
}
import org.enso.projectmanager.infrastructure.repository.{
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

/**
  * Implementation of business logic for project management.
  *
  * @param validator a project validator
  * @param repo a project repository
  * @param log a logging facility
  * @param clock a clock
  * @param gen a random generator
  */
class ProjectService(
  validator: ProjectValidator[IO],
  repo: ProjectRepository[ZIO[ZEnv, *, *]],
  log: Logging[IO],
  clock: Clock[IO],
  gen: Generator[IO]
) extends ProjectServiceApi[ZIO[ZEnv, *, *]] {

  /**
    * Creates a user project.
    *
    * @param name the name of th project
    * @return projectId
    */
  override def createUserProject(
    name: String
  ): ZIO[ZEnv, ProjectServiceFailure, UUID] = {
    // format: off
    for {
      _            <- log.debug(s"Creating project $name.")
      _            <- validateName(name)
      _            <- validateExists(name)
      creationTime <- clock.nowInUtc()
      projectId    <- gen.randomUUID()
      project       = Project(projectId, name, creationTime)
      _            <- repo.insertUserProject(project).mapError(toServiceFailure)
      _            <- log.info(s"Project $project created.")
    } yield projectId
    // format: on
  }

  /**
    * Deletes a user project.
    *
    * @param projectId the project id
    * @return either failure or unit representing success
    */
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
