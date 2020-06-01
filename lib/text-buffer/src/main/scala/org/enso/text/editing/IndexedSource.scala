package org.enso.text.editing

import org.enso.text.buffer.Rope
import org.enso.text.editing.model.Position

/** A source which character positions can be accessed by index.
  *
  * @tparam A a source type
  */
trait IndexedSource[A] {

  /** Converts position relative to a line to an absolute position in the
    * source.
    *
    * @param pos character position.
    * @param source the source text.
    * @return absolute position in the source.
    */
  def toIndex(pos: Position, source: A): Int
}

object IndexedSource {

  def apply[A](implicit is: IndexedSource[A]): IndexedSource[A] = is

  implicit val CharSequenceIndexedSource: IndexedSource[CharSequence] =
    new IndexedSource[CharSequence] {
      override def toIndex(pos: Position, source: CharSequence): Int = {
        val prefix = source.toString.linesIterator.take(pos.line)
        prefix.mkString("\n").length + pos.character
      }
    }

  implicit val RopeIndexedSource: IndexedSource[Rope] =
    new IndexedSource[Rope] {
      override def toIndex(pos: Position, source: Rope): Int = {
        val prefix = source.lines.take(pos.line)
        prefix.characters.length + pos.character
      }
    }
}
