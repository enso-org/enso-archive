package org.enso.languageserver.text

import org.enso.languageserver.filemanager.Path

object model {

  case class Position(line: Int, character: Int)

  case class Range(start: Position, end: Position)

  case class TextEdit(range: Range, text: String)

  case class FileEdit(
    path: Path,
    edits: List[TextEdit],
    oldVersion: Buffer.Version,
    newVersion: Buffer.Version
  )

}
