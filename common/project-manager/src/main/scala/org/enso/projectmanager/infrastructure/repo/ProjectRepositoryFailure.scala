package org.enso.projectmanager.infrastructure.repo

sealed trait ProjectRepositoryFailure

object ProjectRepositoryFailure {

  case class CannotLoadMetadata(msg: String) extends ProjectRepositoryFailure

  case class StorageFailure(msg: String) extends ProjectRepositoryFailure

}
