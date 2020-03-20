package org.enso.projectmanager.infrastructure.repo

import java.util.UUID

case class ProjectIndex(
  userProjects: Map[UUID, ProjectMetadata]      = Map.empty,
  sampleProjects: List[ProjectMetadata]         = List.empty,
  temporaryProjects: Map[UUID, ProjectMetadata] = Map.empty
) {

  def updateProject[E](projectId: UUID)(
    f: Option[ProjectMetadata] => Either[E, ProjectMetadata]
  ): Either[E, ProjectIndex] = {
    val maybeProject = userProjects.get(projectId)
    val maybeUpdate  = f(maybeProject)
    maybeUpdate.map { project =>
      val updatedProjects = userProjects + (projectId -> project)
      ProjectIndex(updatedProjects)
    }
  }

  def exists(name: String): Boolean = userProjects.values.exists(_.name == name)

}

object ProjectIndex {

  val Empty = ProjectIndex()

}
