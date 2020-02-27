package org.enso.languageserver.data.buffer

case class Buffer(contents: Rope) {
  def applyEdit(edit: Edit): Buffer ={
???
  }
}

case class Edit(range: Range, newContents: String)

case class Offset(row: Int, column: Int) extends Ordered[Offset] {
  override def compare(
    that: Offset
  ): Int =
    if (this.row < that.row) -1
    else if (this.row == that.row) {
      this.column - that.column
    } else 1
}

case class Range(start: Offset, end: Offset)
