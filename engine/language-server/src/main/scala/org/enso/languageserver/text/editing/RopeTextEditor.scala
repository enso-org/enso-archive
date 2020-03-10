package org.enso.languageserver.text.editing

import org.enso.languageserver.data.buffer.Rope
import org.enso.languageserver.text.editing.model.TextEdit

object RopeTextEditor extends TextEditor[Rope] {

  override def edit(buffer: Rope, diff: TextEdit): Rope = {
    val head: Rope = cutOutHead(buffer, diff)
    val tail: Rope = cutOutTail(buffer, diff)

    head ++ Rope(diff.text) ++ tail
  }

  def cutOutHead(buffer: Rope, diff: TextEdit): Rope = {
    val fullLines =
      if (diff.range.start.line > 0)
        buffer.lines.take(diff.range.start.line)
      else
        Rope.empty

    val rest =
      if (diff.range.start.character > 0)
        buffer.lines
          .drop(diff.range.start.line)
          .codePoints
          .take(diff.range.start.character)
      else
        Rope.empty

    fullLines ++ rest
  }

  def cutOutTail(buffer: Rope, diff: TextEdit): Rope =
    buffer.lines
      .drop(diff.range.end.line)
      .codePoints
      .drop(diff.range.end.character)

  override def getLineCount(buffer: Rope): Int = buffer.lines.length

  override def getLine(buffer: Rope, lineNumber: Int): String = {
    val tail = buffer.lines.drop(lineNumber)
    val line = tail.lines.take(1)
    line.toString
  }
}
