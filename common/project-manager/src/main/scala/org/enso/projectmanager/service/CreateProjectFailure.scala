package org.enso.projectmanager.service

sealed trait CreateProjectFailure

object CreateProjectFailure {

  case class ValidationFailure(msg: String) extends CreateProjectFailure

  case class DataStoreFailure(msg: String) extends CreateProjectFailure

  case object ProjectExists extends CreateProjectFailure

}
