package org.enso.projectmanager.infrastructure.repository

import java.util.UUID

import org.enso.projectmanager.model.Project

/**
  * An abstraction for accessing project domain objects from durable storage.
  *
  * @tparam F a monadic context
  */
trait ProjectRepository[F[+_, +_]] {

  /**
    * Tests if project is present in the data storage.
    *
    * @param name a project name
    * @return true if project exists
    */
  def exists(name: String): F[ProjectRepositoryFailure, Boolean]

  /**
    * Upsert the provided user project in the storage.
    *
    * @param project the project to insert
    * @return
    */
  def upsertUserProject(
    project: Project
  ): F[ProjectRepositoryFailure, Unit]

  /**
    * Removes the provided project from the storage.
    *
    * @param projectId the project id to remove
    * @return either failure or success
    */
  def deleteUserProject(projectId: UUID): F[ProjectRepositoryFailure, Unit]

  /**
    * Finds a project by project id.
    *
    * @param projectId a project id
    * @return option with the project entity
    */
  def findUserProject(
    projectId: UUID
  ): F[ProjectRepositoryFailure, Option[Project]]

  def listRecent(size: Int): F[ProjectRepositoryFailure, List[Project]]

}
