package org.enso.flexer

import org.enso.flexer.Vocabulary.Range

import scala.collection.immutable

class Vocabulary {
  var divisions = immutable.SortedSet[Int](0, Int.MaxValue)

  def insert(range: Range): Unit = {
    divisions = divisions + range.start
    divisions = divisions + (range.end + 1)
  }

  def size: Int = divisions.size - 1

  override def toString: String =
    "Vocabulary(" + divisions.toList.map(_.toString).mkString(",") + ")"

  def iter[U]: Iterator[(Range, Int)] = {
    divisions.iterator.zip(divisions.iterator.drop(1)).zipWithIndex.map {
      case ((start, end), ix) => (Range(start, end - 1), ix)
    }
  }
}

object Vocabulary {

  case class Range(start: Int, end: Int)

}
