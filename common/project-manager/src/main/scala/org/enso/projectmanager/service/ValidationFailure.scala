package org.enso.projectmanager.service

sealed trait ValidationFailure

object ValidationFailure {

  case object EmptyName extends ValidationFailure

  case class NameContainsForbiddenCharacter(characters: Set[Char])
      extends ValidationFailure

}
