package org.enso.projectmanager.main

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import io.circe.generic.auto._
import org.enso.jsonrpc.JsonRpcServer
import org.enso.projectmanager.infrastructure.execution.ZioEnvExec
import org.enso.projectmanager.infrastructure.file.{
  BlockingFileSystem,
  ZioFileStorage
}
import org.enso.projectmanager.infrastructure.log.Slf4jLogging
import org.enso.projectmanager.infrastructure.random.SystemGenerator
import org.enso.projectmanager.infrastructure.repository.{
  ProjectFileRepository,
  ProjectIndex
}
import org.enso.projectmanager.infrastructure.time.RealClock
import org.enso.projectmanager.main.configuration.ProjectManagerConfig
import org.enso.projectmanager.protocol.{
  JsonRpc,
  ManagerClientControllerFactory
}
import org.enso.projectmanager.service.{MtlProjectValidator, ProjectService}
import zio._
import zio.interop.catz.core._

/**
  * A main module containing all components of the project manager.
  *
  */
class MainModule(
  config: ProjectManagerConfig,
  runtime: Runtime[ZEnv],
  storageSemaphore: Semaphore
) {

  implicit val system = ActorSystem()

  implicit val materializer = SystemMaterializer.get(system)

  lazy val logging = new Slf4jLogging[ZEnv]

  lazy val clock = new RealClock[ZEnv]

  lazy val exec = new ZioEnvExec(runtime)

  lazy val fileSystem = new BlockingFileSystem(config.timeout.ioTimeout)

  lazy val indexStorage = new ZioFileStorage[ProjectIndex](
    config.storage.projectMetadataPath,
    fileSystem,
    storageSemaphore
  )

  lazy val projectRepository =
    new ProjectFileRepository(config.storage, fileSystem, indexStorage)

  lazy val gen = new SystemGenerator[ZEnv]

  lazy val projectValidator = new MtlProjectValidator[ZIO[ZEnv, *, *]]()

  lazy val projectService =
    new ProjectService[({ type T[+A, +B] = ZIO[ZEnv, A, B] })#T](
      projectValidator,
      projectRepository,
      logging,
      clock,
      gen
    )

  lazy val clientControllerFactory = new ManagerClientControllerFactory(
    system,
    projectService,
    exec,
    config.timeout.requestTimeout
  )

  lazy val server = new JsonRpcServer(JsonRpc.protocol, clientControllerFactory)

}
