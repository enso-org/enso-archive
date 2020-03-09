package org.enso.languageserver.text

import org.enso.languageserver.text.model.TextEdit

trait TextEditor[A] {

  def edit(buffer: A, diff: TextEdit): A

  def applyEdits(buffer: A, diffs: Seq[TextEdit]): A =
    diffs.foldLeft(buffer) { case (buf, diff) => edit(buf, diff) }

}

object TextEditor {

  def apply[A](implicit textEditor: TextEditor[A]): TextEditor[A] = textEditor

}
