package org.enso.projectmanager.services

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.enso.projectmanager.model.Project
import org.enso.projectmanager.model.ProjectsRepository

import scala.collection.immutable.HashMap

sealed trait InternalProjectsCommand
sealed trait ProjectsCommand extends InternalProjectsCommand

case class ListTutorialsRequest(replyTo: ActorRef[ListProjectsResponse])
    extends ProjectsCommand
case class ListProjectsRequest(replyTo: ActorRef[ListProjectsResponse])
    extends ProjectsCommand
case class ListProjectsResponse(projects: HashMap[UUID, Project])

case class GetProjectById(id: UUID, replyTo: ActorRef[GetProjectResponse])
    extends ProjectsCommand
case class GetProjectResponse(project: Option[Project])

case class CreateTemporary(
  name: String,
  replyTo: ActorRef[CreateTemporaryResponse])
    extends ProjectsCommand
case class CreateTemporaryResponse(id: UUID, project: Project)

case object TutorialsReady extends InternalProjectsCommand

object ProjectsService {

  def behavior(
    storageManager: StorageManager,
    tutorialsDownloader: TutorialsDownloader
  ): Behavior[InternalProjectsCommand] = Behaviors.setup { context =>
    val buffer = StashBuffer[InternalProjectsCommand](capacity = 100)

    def handle(
      localRepo: ProjectsRepository,
      tutorialsRepo: Option[ProjectsRepository]
    ): Behavior[InternalProjectsCommand] = Behaviors.receiveMessage {
      case ListProjectsRequest(replyTo) =>
        replyTo ! ListProjectsResponse(localRepo.projects)
        Behaviors.same
      case msg: ListTutorialsRequest =>
        tutorialsRepo match {
          case Some(repo) => msg.replyTo ! ListProjectsResponse(repo.projects)
          case None       => buffer.stash(msg)
        }
        Behaviors.same
      case GetProjectById(id, replyTo) =>
        val project =
          localRepo.getById(id).orElse(tutorialsRepo.flatMap(_.getById(id)))
        replyTo ! GetProjectResponse(project)
        Behaviors.same
      case TutorialsReady =>
        val newTutorialsRepo = storageManager.readTutorials
        buffer.unstashAll(context, handle(localRepo, Some(newTutorialsRepo)))
      case msg: CreateTemporary =>
        val project =
          storageManager.createTemporary(msg.name)
        val (projectId, newProjectsRepo) = localRepo.insert(project)
        msg.replyTo ! CreateTemporaryResponse(projectId, project)
        handle(newProjectsRepo, tutorialsRepo)
    }

    context.pipeToSelf(tutorialsDownloader.run())(_ => TutorialsReady)

    handle(storageManager.readLocalProjects, None)
  }
}
