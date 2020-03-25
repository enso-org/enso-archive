package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.enso.projectmanager.control.core.CovariantFlatMap
import org.enso.projectmanager.control.core.syntax._
import org.enso.projectmanager.control.effect.syntax._
import org.enso.projectmanager.control.effect.{Async, ErrorChannel}
import org.enso.projectmanager.data.SocketData
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerProtocol._
import org.enso.projectmanager.boot.configuration.TimeoutConfig
import org.enso.projectmanager.model.Project

import scala.concurrent.ExecutionContext

class LanguageServerControllerProxy[F[+_, +_]: Async: ErrorChannel: CovariantFlatMap](
  controller: ActorRef,
  timeoutConfig: TimeoutConfig
)(implicit ec: ExecutionContext)
    extends LanguageServerService[F] {

  implicit val timeout: Timeout = Timeout(timeoutConfig.bootTimeout)

  override def start(
    clientId: UUID,
    project: Project
  ): F[ServerStartupFailure, SocketData] = {
    Async[F]
      .fromFuture { () =>
        (controller ? StartServer(clientId, project)).mapTo[ServerStartupResult]
      }
      .mapError(_ => ServerBootTimedOut)
      .flatMap {
        case ServerStarted(socket)   => CovariantFlatMap[F].pure(socket)
        case f: ServerStartupFailure => ErrorChannel[F].fail(f)
      }

  }
}
