package org.enso.projectmanager.protocol

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import org.enso.jsonrpc.ClientControllerFactory
import org.enso.projectmanager.infrastructure.execution.Exec
import org.enso.projectmanager.service.ProjectServiceApi
import zio.{ZEnv, ZIO}

import scala.concurrent.duration.FiniteDuration

/**
  * Project manager client controller factory.
  *
  * @param system the actor system
  */
class ManagerClientControllerFactory(
  system: ActorSystem,
  projectService: ProjectServiceApi[({ type T[+A, +B] = ZIO[ZEnv, A, B] })#T],
  exec: Exec[ZIO[ZEnv, *, *]],
  requestTimeout: FiniteDuration
) extends ClientControllerFactory {

  /**
    * Creates a client controller actor.
    *
    * @param clientId the internal client id.
    * @return an actor ref to the client controller
    */
  override def createClientController(clientId: UUID): ActorRef =
    system.actorOf(
      ClientController.props(clientId, projectService, exec, requestTimeout)
    )

}
