package org.enso.projectmanager.service

import java.util.UUID

import org.enso.projectmanager.data.SocketData

/**
  * A contract for the Project Service.
  *
  * @tparam F target bifunctor
  */
trait ProjectServiceApi[F[+_, +_]] {

  /**
    * Creates a user project.
    *
    * @param name the name of th project
    * @return projectId
    */
  def createUserProject(name: String): F[ProjectServiceFailure, UUID]

  /**
    * Deletes a user project.
    *
    * @param projectId the project id
    * @return either failure or unit representing success
    */
  def deleteUserProject(projectId: UUID): F[ProjectServiceFailure, Unit]

  def openProject(
    clientId: UUID,
    projectId: UUID
  ): F[ProjectServiceFailure, SocketData]

  def closeProject(
    clientId: UUID,
    projectId: UUID
  ): F[ProjectServiceFailure, Unit]

}
