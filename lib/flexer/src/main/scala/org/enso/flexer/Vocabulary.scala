package org.enso.flexer

import scala.collection.immutable

class Vocabulary extends Iterable[(Range, Int)] {
  var divisions = immutable.SortedSet[Int](0, Int.MaxValue)

  def insert(range: Range): Unit = {
    divisions = divisions + range.start
    divisions = divisions + (range.end + 1)
  }

  override def size: Int = divisions.size - 1

  override def toString: String =
    "Vocabulary(" + divisions.toList.map(_.toString).mkString(",") + ")"

  override def iterator: Iterator[(Range, Int)] =
    divisions.iterator.zip(divisions.iterator.drop(1)).zipWithIndex.map {
      case ((start, end), ix) => (start to end - 1, ix)
    }
}
