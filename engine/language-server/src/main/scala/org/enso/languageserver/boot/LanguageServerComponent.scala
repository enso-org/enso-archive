package org.enso.languageserver.boot

import akka.http.scaladsl.Http
import com.typesafe.scalalogging.LazyLogging
import org.enso.languageserver.LanguageProtocol
import org.enso.languageserver.boot.LanguageServerComponent.{
  ServerRestarted,
  ServerStarted,
  ServerStopped
}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A lifecycle component used to start and stop a Language Server.
  *
  * @param config a LS config
  */
class LanguageServerComponent(config: LanguageServerConfig)
    extends LazyLogging {

  @volatile
  private var maybeServerState: Option[(MainModule, Http.ServerBinding)] = None

  implicit private val ec = config.computeExecutionContext

  /**
    * Starts asynchronously a server.
    *
    * @return a notice that the server started successfully
    */
  def start(): Future[ServerStarted.type] = {
    logger.info("Starting Language Server...")
    for {
      mainModule <- Future { new MainModule(config) }
      _          <- Future { mainModule.languageServer ! LanguageProtocol.Initialize }
      binding    <- mainModule.server.bind(config.interface, config.port)
      _          <- Future { maybeServerState = Some((mainModule, binding)) }
      _ <- Future {
        logger.info(s"Started server at ${config.interface}:${config.port}")
      }
    } yield ServerStarted
  }

  /**
    * Stops asynchronously a server.
    *
    * @return a notice that the server stopped successfully
    */
  def stop(): Future[ServerStopped.type] =
    maybeServerState match {
      case None =>
        Future.failed(new Exception("Server isn't running"))

      case Some((mainModule, binding)) =>
        for {
          _ <- binding.terminate(10.seconds)
          _ <- mainModule.system.terminate()
          _ <- Future { mainModule.context.close(true) }
          _ <- Future { maybeServerState = None }
        } yield ServerStopped
    }

  def restart(): Future[ServerRestarted.type] =
    for {
      _ <- forceStop()
      _ <- start()
    } yield ServerRestarted

  private def forceStop(): Future[Unit] = {
    maybeServerState match {
      case None =>
        Future.successful(())

      case Some((mainModule, binding)) =>
        for {
          _ <- binding.terminate(10.seconds).recover(logError)
          _ <- mainModule.system.terminate().recover(logError)
          _ <- Future { mainModule.context.close(true) }.recover(logError)
          _ <- Future { maybeServerState = None }
        } yield ServerStopped
    }
  }

  private val logError: PartialFunction[Throwable, Unit] = {
    case th => logger.error("An error occurred during stopping server", th)
  }

}

object LanguageServerComponent {

  case object ServerStarted

  case object ServerStopped

  case object ServerRestarted

}
