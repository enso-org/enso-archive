package org.enso.projectmanager.service

import java.util.UUID

import cats.MonadError
import org.enso.projectmanager.control.core.CovariantFlatMap
import org.enso.projectmanager.control.core.syntax._
import org.enso.projectmanager.control.effect.ErrorChannel
import org.enso.projectmanager.control.effect.syntax._
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol.{
  ServerBootFailed,
  ServerBootTimedOut
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerService
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
  LanguageServerStartupFailed,
  ProjectExists,
  ProjectNotFound
}
import org.enso.projectmanager.service.ValidationFailure.{
  EmptyName,
  NameContainsForbiddenCharacter
}

/**
  * Implementation of business logic for project management.
  *
  * @param validator a project validator
  * @param repo a project repository
  * @param log a logging facility
  * @param clock a clock
  * @param gen a random generator
  */
class ProjectService[F[+_, +_]: ErrorChannel: CovariantFlatMap](
  validator: ProjectValidator[F],
  repo: ProjectRepository[F],
  log: Logging[F],
  clock: Clock[F],
  gen: Generator[F],
  languageServerService: LanguageServerService[F]
)(implicit E: MonadError[F[ProjectServiceFailure, *], ProjectServiceFailure])
    extends ProjectServiceApi[F] {

  import E._

  /**
    * Creates a user project.
    *
    * @param name the name of th project
    * @return projectId
    */
  override def createUserProject(
    name: String
  ): F[ProjectServiceFailure, UUID] = {
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
  ): F[ProjectServiceFailure, Unit] =
    log.debug(s"Deleting project $projectId.") *>
    repo.deleteUserProject(projectId).mapError(toServiceFailure) *>
    log.info(s"Project $projectId deleted.")

  override def openProject(
    clientId: UUID,
    projectId: UUID
  ): F[ProjectServiceFailure, SocketData] = {
    for {
      _       <- log.debug(s"Opening project $projectId")
      project <- getUserProject(projectId)
      socket <- languageServerService
        .start(clientId, project)
        .mapError {
          case ServerBootTimedOut =>
            LanguageServerStartupFailed("Language server boot timed out")

          case ServerBootFailed(th) =>
            LanguageServerStartupFailed(
              s"Language server boot failed: ${th.getMessage}"
            )
        }
    } yield socket
  }

  private def getUserProject(
    projectId: UUID
  ): F[ProjectServiceFailure, Project] =
    repo
      .findUserProject(projectId)
      .mapError(toServiceFailure)
      .flatMap {
        case None          => ErrorChannel[F].fail(ProjectNotFound)
        case Some(project) => CovariantFlatMap[F].pure(project)
      }

  private def validateExists(
    name: String
  ): F[ProjectServiceFailure, Unit] =
    repo
      .exists(name)
      .mapError(toServiceFailure)
      .flatMap { exists =>
        if (exists) raiseError(ProjectExists)
        else unit
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
  ): F[ProjectServiceFailure, Unit] =
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
