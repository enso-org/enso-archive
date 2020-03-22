package org.enso.projectmanager.infrastructure.repository

import java.io.File
import java.util.UUID

import org.enso.pkg.Package
import org.enso.projectmanager.infrastructure.file.{FileStorage, FileSystem}
import org.enso.projectmanager.infrastructure.repository.ProjectRepositoryFailure.{
  InconsistentStorage,
  ProjectNotFoundInIndex,
  StorageFailure
}
import org.enso.projectmanager.main.configuration.StorageConfig
import org.enso.projectmanager.model.Project
import zio.blocking._
import zio.{ZEnv, ZIO}

class ProjectFileRepository(
  storageConfig: StorageConfig,
  fileSystem: FileSystem[ZIO[ZEnv, *, *]],
  indexStorage: FileStorage[ProjectIndex, ZIO[ZEnv, *, *]]
) extends ProjectRepository[ZIO[ZEnv, *, *]] {

  override def exists(
    name: String
  ): ZIO[ZEnv, ProjectRepositoryFailure, Boolean] =
    indexStorage
      .load()
      .map(_.exists(name))
      .mapError(_.fold(convertFileStorageFailure))

  override def createUserProject(
    project: Project
  ): ZIO[ZEnv, ProjectRepositoryFailure, Unit] = {
    val projectPath     = new File(storageConfig.userProjectsPath, project.name)
    val projectWithPath = project.copy(path = Some(projectPath.toString))

    createProjectStructure(project, projectPath) *>
    indexStorage
      .modify { index =>
        val updated = index.addUserProject(projectWithPath)
        (updated, ())
      }
      .mapError(_.fold(convertFileStorageFailure))
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
    indexStorage
      .modify { index =>
        val maybeProject = index.findUserProject(projectId)
        index.removeUserProject(projectId) -> maybeProject
      }
      .mapError(_.fold(convertFileStorageFailure))
      .flatMap {
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

}
