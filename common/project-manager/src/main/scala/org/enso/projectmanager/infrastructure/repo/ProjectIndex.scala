package org.enso.projectmanager.infrastructure.repo

import java.util.UUID

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

}
