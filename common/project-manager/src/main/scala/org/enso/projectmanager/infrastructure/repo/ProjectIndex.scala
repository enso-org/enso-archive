package org.enso.projectmanager.infrastructure.repo

import java.util.UUID

import org.enso.projectmanager.model.ProjectMetadata

case class ProjectIndex(
  userProjects: Map[UUID, ProjectMetadata]      = Map.empty,
  sampleProjects: List[ProjectMetadata]         = List.empty,
  temporaryProjects: Map[UUID, ProjectMetadata] = Map.empty
) {

  def addUserProject(project: ProjectMetadata): ProjectIndex =
    ProjectIndex(userProjects + (project.id -> project))

  def removeUserProject(projectId: UUID): ProjectIndex =
    ProjectIndex(userProjects - projectId)

  def findUserProject(projectId: UUID): Option[ProjectMetadata] =
    userProjects.get(projectId)

  def exists(name: String): Boolean = userProjects.values.exists(_.name == name)

}

object ProjectIndex {

  val Empty = ProjectIndex()

}
