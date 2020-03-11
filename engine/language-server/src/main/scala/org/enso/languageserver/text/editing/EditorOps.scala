package org.enso.languageserver.text.editing

import cats.implicits._
import org.enso.languageserver.text.editing.TextEditValidator.validate
import org.enso.languageserver.text.editing.model.TextEdit

object EditorOps {

  type EditorOp[A] = Either[TextEditValidationFailure, A]

  def edit[A: TextEditor](buffer: A, diff: TextEdit): EditorOp[A] =
    validate(buffer, diff).map(_ => TextEditor[A].edit(buffer, diff))

  def applyEdits[A: TextEditor](buffer: A, diffs: Seq[TextEdit]): EditorOp[A] =
    diffs.foldLeft[EditorOp[A]](Right(buffer)) {
      case (maybeBuffer, diff) => maybeBuffer >>= (edit(_, diff))
    }

}
