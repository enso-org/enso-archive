package org.enso.projectmanager.main

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import org.enso.jsonrpc.JsonRpcServer
import org.enso.projectmanager.infrastructure.execution.ZioEnvExec
import org.enso.projectmanager.infrastructure.file.FileSystem
import org.enso.projectmanager.infrastructure.log.Slf4jLogging
import org.enso.projectmanager.infrastructure.repo.FileBasedProjectRepository
import org.enso.projectmanager.infrastructure.time.RealClock
import org.enso.projectmanager.main.configuration.ProjectManagerConfig
import org.enso.projectmanager.protocol.{
  JsonRpc,
  ManagerClientControllerFactory
}
import org.enso.projectmanager.service.{ProjectService, ProjectValidator}
import pureconfig.ConfigSource
import zio._
import org.enso.projectmanager.infrastructure.config.ConfigurationReaders.fileReader
import pureconfig._
import pureconfig.generic.auto._

/**
  * A main module containing all components of the project manager.
  *
  */
class MainModule(
  runtime: Runtime[ZEnv],
  storageSemaphore: Semaphore
) {

  val config: ProjectManagerConfig =
    ConfigSource
      .resources("application.conf")
      .withFallback(ConfigSource.systemProperties)
      .at("project-manager")
      .loadOrThrow[ProjectManagerConfig]

  implicit val system = ActorSystem()

  implicit val materializer = SystemMaterializer.get(system)

  lazy val logging = Slf4jLogging

  lazy val clock = RealClock

  lazy val exec = new ZioEnvExec(runtime)

  lazy val fileSystem = new FileSystem(config.timeout.ioTimeout)

  lazy val projectRepository =
    new FileBasedProjectRepository(config.storage, fileSystem, storageSemaphore)

  lazy val projectService =
    new ProjectService(ProjectValidator, projectRepository, logging, clock)

  lazy val clientControllerFactory = new ManagerClientControllerFactory(
    system,
    projectService,
    exec,
    config.timeout.requestTimeout
  )

  lazy val server = new JsonRpcServer(JsonRpc.protocol, clientControllerFactory)

}
