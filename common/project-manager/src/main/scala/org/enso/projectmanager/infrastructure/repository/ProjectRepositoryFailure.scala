package org.enso.projectmanager.infrastructure.repository

sealed trait ProjectRepositoryFailure

object ProjectRepositoryFailure {

  case class CannotLoadIndex(msg: String) extends ProjectRepositoryFailure

  case class StorageFailure(msg: String) extends ProjectRepositoryFailure

  case object ProjectNotFoundInIndex extends ProjectRepositoryFailure

  case class InconsistentStorage(msg: String) extends ProjectRepositoryFailure

}
