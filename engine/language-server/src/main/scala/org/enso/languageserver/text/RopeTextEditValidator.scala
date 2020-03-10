package org.enso.languageserver.text

import org.enso.languageserver.data.buffer.Rope
import cats.implicits._

object RopeTextEditValidator extends TextEditValidator[Rope] {

  override def validate(
    buffer: Rope,
    textEdit: model.TextEdit
  ): Either[TextEditValidationFailure, Unit] = {
    for {
      _ <- checkIfEndIsAfterStart(textEdit)
      _ <- checkPosition(textEdit.range.start)
      _ <- checkPosition(textEdit.range.end)
    } yield ()
  }

  private def checkIfEndIsAfterStart(
    textEdit: model.TextEdit
  ): Either[TextEditValidationFailure, Unit] =
    if (textEdit.range.end < textEdit.range.start) {
      Left(EndPositionBeforeStartPosition)
    } else {
      Right(())
    }

  private def checkPosition(
    start: model.Position
  ): Either[TextEditValidationFailure, Unit] =
    checkIfNotNegative(start.line) >> checkIfNotNegative(start.character)

  private def checkIfNotNegative(
    coord: Int
  ): Either[TextEditValidationFailure, Unit] =
    if (coord >= 0) Right(())
    else Left(NegativeCoordinateInPosition)

}
