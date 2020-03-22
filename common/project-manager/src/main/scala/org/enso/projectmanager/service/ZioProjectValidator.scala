package org.enso.projectmanager.service

import org.enso.projectmanager.service.ValidationFailure.{
  EmptyName,
  NameContainsForbiddenCharacter
}
import zio.IO

/**
  * ZIO implementation of the project validator.
  */
object ZioProjectValidator extends ProjectValidator[IO] {

  private val validCharSpec: Char => Boolean = { char =>
    char.isLetterOrDigit || char == '_' || char == '-'
  }

  /**
    * Validates a project name.
    *
    * @param name the project name
    * @return either validation failure or success
    */
  override def validateName(name: String): IO[ValidationFailure, Unit] =
    checkIfNonEmptyName(name) *> checkCharacters(name)

  private def checkIfNonEmptyName(name: String): IO[ValidationFailure, Unit] =
    if (name.trim.isEmpty) {
      IO.fail(EmptyName)
    } else {
      IO.unit
    }

  private def checkCharacters(name: String): IO[ValidationFailure, Unit] = {
    val forbiddenChars = name.toCharArray.filterNot(validCharSpec).toSet
    if (forbiddenChars.isEmpty) IO.unit
    else IO.fail(NameContainsForbiddenCharacter(forbiddenChars))
  }

}
