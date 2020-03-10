package org.enso.languageserver.text

import org.enso.languageserver.data.buffer.Rope

object RopeTextEditValidator extends TextEditValidator[Rope] {

  override def validate(
    buffer: Rope,
    textEdit: model.TextEdit
  ): Either[TextEditValidationFailure, Unit] = {
    for {
      _ <- checkIfEndIsAfterStart(textEdit)
    } yield ()
  }

  def checkIfEndIsAfterStart(
    textEdit: model.TextEdit
  ): Either[TextEditValidationFailure, Unit] =
    if (textEdit.range.end < textEdit.range.start) {
      Left(EndPositionBeforeStartPosition)
    } else {
      Right(())
    }

}
