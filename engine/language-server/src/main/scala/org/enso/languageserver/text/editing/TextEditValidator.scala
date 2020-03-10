package org.enso.languageserver.text.editing

import org.enso.languageserver.text.editing.model.TextEdit
import cats.implicits._

object TextEditValidator {

  def validate[A: TextEditor](
    buffer: A,
    textEdit: TextEdit
  ): Either[TextEditValidationFailure, Unit] = {
    for {
      _ <- checkIfEndIsAfterStart(textEdit)
      _ <- checkPosition(textEdit.range.start)
      _ <- checkPosition(textEdit.range.end)
      _ <- checkIfInsideBuffer(buffer, textEdit.range.start)
      _ <- checkIfInsideBuffer(buffer, textEdit.range.end)
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

  def checkIfInsideBuffer[A: TextEditor](
    buffer: A,
    position: model.Position
  ): Either[TextEditValidationFailure, Unit] = {
    val lineCount = TextEditor[A].getLineCount(buffer)
    if (position.line >= lineCount) {
      Left(PositionNotFound(position))
    } else {
      val line = TextEditor[A].getLine(buffer, position.line)
      if (position.character > line.length) {
        Left(PositionNotFound(position))
      } else {
        Right(())
      }
    }
  }

}
