package org.enso.projectmanager.main

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import org.enso.jsonrpc.JsonRpcServer
import org.enso.projectmanager.infrastructure.execution.ZioEnvExec
import org.enso.projectmanager.infrastructure.file.BlockingFileSystem
import org.enso.projectmanager.infrastructure.log.Slf4jLogging
import org.enso.projectmanager.infrastructure.random.{
  Generator,
  SystemGenerator
}
import org.enso.projectmanager.infrastructure.repo.FileBasedProjectRepository
import org.enso.projectmanager.infrastructure.time.RealClock
import org.enso.projectmanager.main.configuration.ProjectManagerConfig
import org.enso.projectmanager.protocol.{
  JsonRpc,
  ManagerClientControllerFactory
}
import org.enso.projectmanager.service.{ProjectService, ZioProjectValidator}
import zio._

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

  lazy val logging = Slf4jLogging

  lazy val clock = RealClock

  lazy val exec = new ZioEnvExec(runtime)

  lazy val fileSystem = new BlockingFileSystem(config.timeout.ioTimeout)

  lazy val projectRepository =
    new FileBasedProjectRepository(config.storage, fileSystem, storageSemaphore)

  lazy val gen = SystemGenerator

  lazy val projectService =
    new ProjectService(
      ZioProjectValidator,
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
