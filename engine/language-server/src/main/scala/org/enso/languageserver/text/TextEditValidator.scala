package org.enso.languageserver.text

import org.enso.languageserver.text.model.TextEdit

trait TextEditValidator[A] {

  def validate(
    buffer: A,
    textEdit: TextEdit
  ): Either[TextEditValidationFailure, Unit]

}
