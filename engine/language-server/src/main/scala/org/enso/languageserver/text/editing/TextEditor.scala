package org.enso.languageserver.text.editing

import org.enso.languageserver.data.buffer.Rope
import org.enso.languageserver.text.editing.model.TextEdit

trait TextEditor[A] {

  def edit(buffer: A, diff: TextEdit): A

  def getLineCount(buffer: A): Int

  def getLine(buffer: A, lineNumber: Int): String

}

object TextEditor {

  def apply[A](implicit textEditor: TextEditor[A]): TextEditor[A] = textEditor

  implicit val ropeTextEditor: TextEditor[Rope] = RopeTextEditor

}
