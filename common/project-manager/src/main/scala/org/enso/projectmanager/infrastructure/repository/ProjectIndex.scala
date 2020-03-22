package org.enso.projectmanager.infrastructure.repository

import java.util.UUID

import org.enso.projectmanager.data.Default
import org.enso.projectmanager.model.Project

case class ProjectIndex(
  userProjects: Map[UUID, Project]      = Map.empty,
  sampleProjects: List[Project]         = List.empty,
  temporaryProjects: Map[UUID, Project] = Map.empty
) {

  def addUserProject(project: Project): ProjectIndex =
    ProjectIndex(userProjects + (project.id -> project))

  def removeUserProject(projectId: UUID): ProjectIndex =
    ProjectIndex(userProjects - projectId)

  def findUserProject(projectId: UUID): Option[Project] =
    userProjects.get(projectId)

  def exists(name: String): Boolean = userProjects.values.exists(_.name == name)

}

object ProjectIndex {

  val Empty = ProjectIndex()

  implicit val indexDefault: Default[ProjectIndex] = Default.Val(Empty)

}
