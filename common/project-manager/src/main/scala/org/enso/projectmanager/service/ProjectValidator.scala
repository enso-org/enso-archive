package org.enso.projectmanager.service

import org.enso.projectmanager.service.ValidationFailure.{
  EmptyName,
  NameContainsForbiddenCharacter
}
import zio.IO

object ProjectValidator extends ProjectValidatorApi {

  private val ForbiddenCharacters = " \t\\/".toCharArray.toList

  override def validateName(name: String): IO[ValidationFailure, Unit] =
    checkIfNonEmptyName(name) *> checkCharacters(name)

  private def checkIfNonEmptyName(name: String): IO[ValidationFailure, Unit] = {
    if (name.trim.isEmpty) {
      IO.fail(EmptyName)
    } else {
      IO.unit
    }
  }

  private def checkCharacters(name: String): IO[ValidationFailure, Unit] = {
    val maybeForbiddenChar = ForbiddenCharacters.find(name.contains(_: Char))
    maybeForbiddenChar.fold[IO[ValidationFailure, Unit]](IO.unit)(
      ch => IO.fail(NameContainsForbiddenCharacter(ch): ValidationFailure)
    )
  }

}
