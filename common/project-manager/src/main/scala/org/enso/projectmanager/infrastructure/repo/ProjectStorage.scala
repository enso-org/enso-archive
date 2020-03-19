package org.enso.projectmanager.infrastructure.repo

import java.util.UUID

case class ProjectStorage(
  userProjects: Map[UUID, ProjectMetadata] = Map.empty
) {

  def updateProject[E](projectId: UUID)(
    f: Option[ProjectMetadata] => Either[E, ProjectMetadata]
  ): Either[E, ProjectStorage] = {
    val maybeProject = userProjects.get(projectId)
    val maybeUpdate  = f(maybeProject)
    maybeUpdate.map { project =>
      val updatedProjects = userProjects + (projectId -> project)
      ProjectStorage(updatedProjects)
    }
  }

  def exists(name: String): Boolean = userProjects.values.exists(_.name == name)

}

object ProjectStorage {

  val Empty = ProjectStorage()

}
