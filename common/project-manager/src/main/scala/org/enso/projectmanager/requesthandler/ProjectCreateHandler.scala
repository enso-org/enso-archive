package org.enso.projectmanager.requesthandler

import akka.actor._
import akka.pattern.pipe
import org.enso.jsonrpc.Errors.ServiceError
import org.enso.jsonrpc._
import org.enso.projectmanager.infrastructure.execution.Exec
import org.enso.projectmanager.protocol.ProjectManagementApi.ProjectCreate
import org.enso.projectmanager.service.ProjectServiceApi
import zio._

import scala.concurrent.duration.FiniteDuration

class ProjectCreateHandler(
  service: ProjectServiceApi,
  exec: Exec[ZIO[ZEnv, *, *]],
  requestTimeout: FiniteDuration
) extends Actor
    with ActorLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  private def requestStage: Receive = {
    case Request(ProjectCreate, id, params: ProjectCreate.Params) =>
      exec.exec(service.createProject(params.name)).pipeTo(self)
      context.system.scheduler
        .scheduleOnce(requestTimeout, self, RequestTimeout)
      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case Status.Failure(ex) =>
      log.error(s"Failure during $ProjectCreate operation:", ex)
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case RequestTimeout =>
      log.error(s"Request $id timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case Left(failure) =>
      log.error(s"Request $id failed due to $failure")
      replyTo ! ResponseError(Some(id), ServiceError) //todo
      context.stop(self)

    case Right(()) =>
      replyTo ! ResponseResult(ProjectCreate, id, Unused)
      context.stop(self)
  }

}

object ProjectCreateHandler {

  def props(
    service: ProjectServiceApi,
    exec: Exec[ZIO[ZEnv, *, *]],
    requestTimeout: FiniteDuration
  ): Props =
    Props(new ProjectCreateHandler(service, exec, requestTimeout))

}
