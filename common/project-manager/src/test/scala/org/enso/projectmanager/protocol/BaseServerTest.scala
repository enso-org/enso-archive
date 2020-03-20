package org.enso.projectmanager.protocol

import java.io.File
import java.nio.file.Files

import org.enso.jsonrpc.test.JsonRpcServerTestKit
import org.enso.jsonrpc.{ClientControllerFactory, Protocol}
import org.enso.projectmanager.infrastructure.execution.ZioEnvExec
import org.enso.projectmanager.infrastructure.file.BlockingFileSystem
import org.enso.projectmanager.infrastructure.log.Slf4jLogging
import org.enso.projectmanager.infrastructure.repo.FileBasedProjectRepository
import org.enso.projectmanager.infrastructure.time.RealClock
import org.enso.projectmanager.main.configuration.StorageConfig
import org.enso.projectmanager.service.{ProjectService, ZioProjectValidator}
import zio.{Runtime, Semaphore}

import scala.concurrent.duration._

class BaseServerTest extends JsonRpcServerTestKit {

  override def protocol: Protocol = JsonRpc.protocol

  val testProjectsRoot = Files.createTempDirectory(null).toFile
  testProjectsRoot.deleteOnExit()

  lazy val testStorageConfig = StorageConfig(
    projectsRoot        = testProjectsRoot,
    projectMetadataPath = new File(testProjectsRoot, "project-index.json"),
    userProjectsPath    = new File(testProjectsRoot, "projects")
  )
  lazy val logging = Slf4jLogging

  lazy val clock = RealClock

  lazy val exec = new ZioEnvExec(Runtime.default)

  lazy val fileSystem = new BlockingFileSystem(5.seconds)

  lazy val storageSemaphore =
    Runtime.default.unsafeRun(Semaphore.make(1))

  lazy val projectRepository =
    new FileBasedProjectRepository(
      testStorageConfig,
      fileSystem,
      storageSemaphore
    )

  lazy val projectService =
    new ProjectService(ZioProjectValidator, projectRepository, logging, clock)

  override def clientControllerFactory: ClientControllerFactory = {
    new ManagerClientControllerFactory(system, projectService, exec, 10.seconds)
  }

}
