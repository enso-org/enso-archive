package org.enso.projectmanager.service

import java.util.UUID

trait ProjectServiceApi[F[_, _]] {

  def createUserProject(name: String): F[CreateProjectFailure, UUID]

}
