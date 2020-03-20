package org.enso.projectmanager.infrastructure.repo

import java.io.File
import java.util.UUID

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.enso.pkg.Package
import org.enso.projectmanager.infrastructure.file.FileSystemApi
import org.enso.projectmanager.infrastructure.file.FileSystemFailure.FileNotFound
import org.enso.projectmanager.infrastructure.repo.ProjectRepositoryFailure.{
  CannotLoadMetadata,
  StorageFailure
}
import org.enso.projectmanager.main.configuration.StorageConfig
import org.enso.projectmanager.model.ProjectMetadata
import zio.blocking._
import zio.{Semaphore, ZEnv, ZIO}

class FileBasedProjectRepository(
  storageConfig: StorageConfig,
  fileSystem: FileSystemApi,
  semaphore: Semaphore
) extends ProjectRepository {

  override def exists(
    name: String
  ): ZIO[ZEnv, ProjectRepositoryFailure, Boolean] =
    loadIndex().map(_.exists(name))

  override def createUserProject(
    project: ProjectMetadata
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] = {
    val projectPath     = new File(storageConfig.userProjectsPath, project.name)
    val projectWithPath = project.copy(path = Some(projectPath.toString))
    createProjectStructure(project, projectPath) *>
    compareAndSetMetadata(project.id) {
      case None    => Right(projectWithPath)
      case Some(_) => throw new RuntimeException("UUID collision")
      //it is impossible
    }
  }

  private def createProjectStructure(
    project: ProjectMetadata,
    projectPath: File
  ): ZIO[Blocking, StorageFailure, Package] =
    effectBlocking(Package.create(projectPath, project.name))
      .mapError(th => StorageFailure(th.toString))

  private def compareAndSetMetadata[E](projectId: UUID)(
    f: Option[ProjectMetadata] => Either[
      ProjectRepositoryFailure,
      ProjectMetadata
    ]
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] = {
    semaphore.withPermit {
      // format: off
      for {
        metadata      <- loadIndex()
        maybeUpdated   = metadata.updateProject(projectId)(f)
        _             <- maybeUpdated.fold(ZIO.fail[ProjectRepositoryFailure](_), persistIndex)
      } yield ()
      // format: on
    }
  }

  private def loadIndex(): ZIO[ZEnv, ProjectRepositoryFailure, ProjectIndex] =
    fileSystem
      .readFile(storageConfig.projectMetadataPath)
      .flatMap { contents =>
        decode[ProjectIndex](contents).fold(
          failure => ZIO.fail(CannotLoadMetadata(failure.getMessage)),
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
