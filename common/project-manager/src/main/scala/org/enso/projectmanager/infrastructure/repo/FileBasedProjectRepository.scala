package org.enso.projectmanager.infrastructure.repo

import java.io.File
import java.util.UUID

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.enso.pkg.Package
import org.enso.projectmanager.infrastructure.file.FileSystem
import org.enso.projectmanager.infrastructure.file.FileSystemFailure.FileNotFound
import org.enso.projectmanager.infrastructure.repo.ProjectRepositoryFailure.{
  CannotLoadIndex,
  InconsistentStorage,
  ProjectNotFoundInIndex,
  StorageFailure
}
import org.enso.projectmanager.main.configuration.StorageConfig
import org.enso.projectmanager.model.Project
import zio.blocking._
import zio.{Semaphore, ZEnv, ZIO}

class FileBasedProjectRepository(
  storageConfig: StorageConfig,
  fileSystem: FileSystem[ZIO[ZEnv, *, *]],
  semaphore: Semaphore
) extends ProjectRepository[ZIO[ZEnv, *, *]] {

  override def exists(
    name: String
  ): ZIO[ZEnv, ProjectRepositoryFailure, Boolean] =
    loadIndex().map(_.exists(name))

  override def createUserProject(
    project: Project
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] = {
    val projectPath     = new File(storageConfig.userProjectsPath, project.name)
    val projectWithPath = project.copy(path = Some(projectPath.toString))

    createProjectStructure(project, projectPath) *>
    modifyIndex { index =>
      val updated = index.addUserProject(projectWithPath)
      (updated, ())
    }
  }

  private def createProjectStructure(
    project: Project,
    projectPath: File
  ): ZIO[Blocking, StorageFailure, Package] =
    effectBlocking { Package.create(projectPath, project.name) }
      .mapError(th => StorageFailure(th.toString))

  override def deleteUserProject(
    projectId: UUID
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] =
    modifyIndex { index =>
      val maybeProject = index.findUserProject(projectId)
      index.removeUserProject(projectId) -> maybeProject
    } flatMap {
      case None =>
        ZIO.fail(ProjectNotFoundInIndex)

      case Some(project) if project.path.isEmpty =>
        ZIO.fail(
          InconsistentStorage(
            "Index cannot contain a user project without path"
          )
        )

      case Some(project) =>
        removeProjectStructure(project.path.get)
    }

  private def removeProjectStructure(
    projectPath: String
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] =
    fileSystem
      .removeDir(new File(projectPath))
      .mapError[ProjectRepositoryFailure](
        failure => StorageFailure(failure.toString)
      )

  private def modifyIndex[A](
    f: ProjectIndex => (ProjectIndex, A)
  ): ZIO[ZEnv, ProjectRepositoryFailure, A] = {
    semaphore.withPermit {
      // format: off
      for {
        index             <- loadIndex()
        (updated, output)  = f(index)
        _                 <- persistIndex(updated)
      } yield output
      // format: on
    }
  }

  private def loadIndex(): ZIO[ZEnv, ProjectRepositoryFailure, ProjectIndex] =
    fileSystem
      .readFile(storageConfig.projectMetadataPath)
      .flatMap { contents =>
        decode[ProjectIndex](contents).fold(
          failure => ZIO.fail(CannotLoadIndex(failure.getMessage)),
          ZIO.succeed(_)
        )
      }
      .foldM(
        failure = {
          case FileNotFound => ZIO.succeed(ProjectIndex.Empty)
          case other        => ZIO.fail(StorageFailure(other.toString))
        },
        success = ZIO.succeed(_)
      )

  private def persistIndex(
    projectsMetadata: ProjectIndex
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] =
    fileSystem
      .overwriteFile(
        storageConfig.projectMetadataPath,
        projectsMetadata.asJson.spaces2
      )
      .mapError[ProjectRepositoryFailure](
        failure => StorageFailure(failure.toString)
      )

}
