package org.enso.languageserver.text.editing

import org.enso.languageserver.text.editing.model.TextEdit

object EditorOps {

  def applyEdits[A: TextEditor](
    buffer: A,
    diffs: Seq[TextEdit]
  ): Either[TextEditValidationFailure, A] =
    diffs.foldLeft[Either[TextEditValidationFailure, A]](Right(buffer)) {
      case (maybeBuffer, diff) =>
        for {
          currentBuffer <- maybeBuffer
          _             <- TextEditValidator.validate(currentBuffer, diff)
          newBuffer = TextEditor[A].edit(currentBuffer, diff)
        } yield newBuffer
    }

}
