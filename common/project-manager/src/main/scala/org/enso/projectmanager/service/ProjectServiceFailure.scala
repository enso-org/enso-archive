package org.enso.projectmanager.service

sealed trait ProjectServiceFailure

object ProjectServiceFailure {

  case class ValidationFailure(msg: String) extends ProjectServiceFailure

  case class DataStoreFailure(msg: String) extends ProjectServiceFailure

  case object ProjectExists extends ProjectServiceFailure

  case object ProjectNotFound extends ProjectServiceFailure

}
