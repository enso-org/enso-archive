package org.enso.interpreter.nodes.util

case class SourceLoc(position: Long, span: Long) {

  def +(that: SourceLoc): SourceLoc =
    SourceLoc(this.position + that.position, this.span + that.span)
}

object SourceLoc {
  def empty() = SourceLoc(position = 0, span = 0)
  def isEmpty(loc: SourceLoc): Boolean = loc.position == 0 && loc.span == 0
}
