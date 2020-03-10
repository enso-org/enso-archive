package org.enso.languageserver.text.editing

import org.enso.languageserver.text.editing.model.TextEdit

trait TextEditValidator[A] {

  def validate(
    buffer: A,
    textEdit: TextEdit
  ): Either[TextEditValidationFailure, Unit]

}
