package org.enso.languageserver.text.editing

import org.enso.languageserver.filemanager.Path
import org.enso.languageserver.text.Buffer

object model {

  case class Position(line: Int, character: Int) extends Ordered[Position] {

    override def compare(that: Position): Int = {
      if (this == that) {
        0
      } else if (isThisBefore(that)) {
        -1
      } else {
        1
      }
    }

    private def isThisBefore(that: Position) =
      this.line < that.line || (this.line == that.line && this.character < that.character)

  }

  case class Range(start: Position, end: Position)

  case class TextEdit(range: Range, text: String)

  case class FileEdit(
    path: Path,
    edits: List[TextEdit],
    oldVersion: Buffer.Version,
    newVersion: Buffer.Version
  )

}
