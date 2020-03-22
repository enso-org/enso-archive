package org.enso.projectmanager.protocol

import java.io.File
import java.nio.file.Files
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

import org.enso.jsonrpc.test.JsonRpcServerTestKit
import org.enso.jsonrpc.{ClientControllerFactory, Protocol}
import org.enso.projectmanager.infrastructure.execution.ZioEnvExec
import org.enso.projectmanager.infrastructure.file.{
  BlockingFileSystem,
  ZioFileStorage
}
import org.enso.projectmanager.infrastructure.repository.{
  ProjectFileRepository,
  ProjectIndex
}
import org.enso.projectmanager.infrastructure.time.RealClock
import org.enso.projectmanager.main.configuration.StorageConfig
import org.enso.projectmanager.service.{ProjectService, ZioProjectValidator}
import org.enso.projectmanager.test.{ConstGenerator, NopLogging, StoppedClock}
import zio.{Runtime, Semaphore}
import io.circe.generic.auto._

import scala.concurrent.duration._

class BaseServerSpec extends JsonRpcServerTestKit {

  override def protocol: Protocol = JsonRpc.protocol

  val TestNow = OffsetDateTime.now(ZoneOffset.UTC)

  val testClock = new StoppedClock(TestNow)

  val TestUUID = UUID.randomUUID()

  lazy val gen = new ConstGenerator(TestUUID)

  val testProjectsRoot = Files.createTempDirectory(null).toFile
  testProjectsRoot.deleteOnExit()

  val userProjectDir = new File(testProjectsRoot, "projects")

  val indexFile = new File(testProjectsRoot, "project-index.json")

  lazy val testStorageConfig = StorageConfig(
    projectsRoot        = testProjectsRoot,
    projectMetadataPath = indexFile,
    userProjectsPath    = userProjectDir
  )
  lazy val clock = RealClock

  lazy val exec = new ZioEnvExec(Runtime.default)

  lazy val fileSystem = new BlockingFileSystem(5.seconds)

  lazy val storageSemaphore =
    Runtime.default.unsafeRun(Semaphore.make(1))

  lazy val indexStorage = new ZioFileStorage[ProjectIndex](
    testStorageConfig.projectMetadataPath,
    fileSystem,
    storageSemaphore
  )

  lazy val projectRepository =
    new ProjectFileRepository(
      testStorageConfig,
      fileSystem,
      indexStorage
    )

  lazy val projectService =
    new ProjectService(
      ZioProjectValidator,
      projectRepository,
      NopLogging,
      clock,
      gen
    )

  override def clientControllerFactory: ClientControllerFactory = {
    new ManagerClientControllerFactory(system, projectService, exec, 10.seconds)
  }

}
