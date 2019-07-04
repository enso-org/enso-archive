package org.enso.projectmanager.services

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.enso.projectmanager.model.Project
import org.enso.projectmanager.model.ProjectId
import org.enso.projectmanager.model.ProjectsRepository

import scala.collection.immutable.HashMap

sealed trait ProjectsServiceCommand
sealed trait ProjectsCommand extends ProjectsServiceCommand
sealed trait ControlCommand  extends ProjectsServiceCommand

trait HasResponse {
  type Response
}

case class Request[T <: HasResponse](
  realRequest: T
)(replyTo: ActorRef[Option[realRequest.Response]]) {}

object Foo {
  val a = Request(???)(???)

  a match {
    case r: Request[ListTutorialsRequest] => r.replyTo
  }
}

case class ListTutorialsRequest(replyTo: ActorRef[Option[ListProjectsResponse]])
    extends ProjectsCommand
case class ListProjectsRequest() extends ProjectsCommand with HasResponse {
  override type Response = ListProjectsResponse
}
case class ListProjectsResponse(projects: HashMap[ProjectId, Project])

case class GetProjectById(
  id: ProjectId,
  replyTo: ActorRef[Option[GetProjectResponse]])
    extends ProjectsCommand
case class GetProjectResponse(project: Option[Project])

case class CreateTemporary(
  name: String,
  replyTo: ActorRef[Option[CreateTemporaryResponse]])
    extends ProjectsCommand
case class CreateTemporaryResponse(id: ProjectId, project: Project)

object ProjectsService {

  def behavior(
    storageManager: StorageManager,
    tutorialsDownloader: TutorialsDownloader
  ): Behavior[ProjectsServiceCommand] = Behaviors.setup { context =>
    val buffer = StashBuffer[ProjectsServiceCommand](capacity = 100)

    def handle(
      localRepo: ProjectsRepository,
      tutorialsRepo: Option[ProjectsRepository]
    ): Behavior[ProjectsServiceCommand] = Behaviors.receiveMessage {
      case ListProjectsRequest(replyTo) =>
        replyTo ! Some(ListProjectsResponse(localRepo.projects))
        Behaviors.same
      case msg: ListTutorialsRequest =>
        tutorialsRepo match {
          case Some(repo) =>
            msg.replyTo ! Some(ListProjectsResponse(repo.projects))
          case None => buffer.stash(msg)
        }
        Behaviors.same
      case GetProjectById(id, replyTo) =>
        val project =
          localRepo.getById(id).orElse(tutorialsRepo.flatMap(_.getById(id)))
        replyTo ! Some(GetProjectResponse(project))
        Behaviors.same
      case msg: CreateTemporary =>
        val project =
          storageManager.createTemporary(msg.name)
        val (projectId, newProjectsRepo) = localRepo.insert(project)
        msg.replyTo ! Some(CreateTemporaryResponse(projectId, project))
        handle(newProjectsRepo, tutorialsRepo)
    }

    handle(storageManager.readLocalProjects, None)
  }
}
