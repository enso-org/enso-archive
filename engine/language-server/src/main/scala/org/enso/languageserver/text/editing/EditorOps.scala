package org.enso.languageserver.text.editing

import cats.implicits._
import org.enso.languageserver.text.editing.TextEditValidator.validate
import org.enso.languageserver.text.editing.model.TextEdit

object EditorOps {

  def edit[A: TextEditor](
    buffer: A,
    diff: TextEdit
  ): Either[TextEditValidationFailure, A] =
    validate(buffer, diff).map(_ => TextEditor[A].edit(buffer, diff))

  def applyEdits[A: TextEditor](
    buffer: A,
    diffs: Seq[TextEdit]
  ): Either[TextEditValidationFailure, A] =
    diffs.foldLeft[Either[TextEditValidationFailure, A]](Right(buffer)) {
      case (maybeBuffer, diff) => maybeBuffer >>= (edit(_, diff))
    }

}
