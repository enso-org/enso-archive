package org.enso.projectmanager.main

import java.io.IOException

import akka.http.scaladsl.Http
import com.typesafe.scalalogging.LazyLogging
import zio.ZIO.effectTotal
import zio._
import zio.console._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Project manager runner containing the main method.
  */
object ProjectManager extends App with LazyLogging {

  logger.info("Starting Language Server...")

  lazy val runtime = Runtime.default

  lazy val mainProcess: ZIO[ZEnv, IOException, Unit] =
    for {
      storageSemaphore <- Semaphore.make(1)
      mainModule = new MainModule(runtime, storageSemaphore)
      _       <- putStrLn(mainModule.config.toString)
      binding <- bindServer(mainModule)
      _ <- effectTotal {
        logger.info(
          s"Started server at ${mainModule.config.server.host}:${mainModule.config.server.port}, press enter to kill server"
        )
      }
      _ <- getStrLn
      _ <- effectTotal { logger.info("Stopping server...") }
      _ <- effectTotal { binding.unbind() }
      _ <- effectTotal { mainModule.system.terminate() }
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    mainProcess.fold(_ => ExitCodes.FailureCode, _ => ExitCodes.SuccessCode)

  private def bindServer(mainModule: MainModule): UIO[Http.ServerBinding] =
    effectTotal {
      Await.result(
        mainModule.server
          .bind(mainModule.config.server.host, mainModule.config.server.port),
        3.seconds
      )
    }

}
