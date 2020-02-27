package org.enso.languageserver.buffer
import org.enso.languageserver.data.buffer.Rope
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}
import org.scalacheck.Arbitrary._

object RopeSpecification extends Properties("Rope") {

  private def ropeFromStrings(strings: List[String]): Rope =
    strings.foldLeft(Rope.empty)((rope, string) => rope ++ Rope(string))

  private def normalizeOffset(offset: Int, length: Int): Int =
    (offset % (length + 1)).abs

  property("Concatenation is consistent with strings") = forAll {
    strings: List[String] =>
      val fromRope = ropeFromStrings(strings).toString
      val naive    = strings.mkString("")
      fromRope == naive
  }

  property("CharRope#take is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr     = strings.mkString("")
      val alignedCount = normalizeOffset(count, naiveStr.length)
      val fromRope =
        ropeFromStrings(strings).characters.take(alignedCount).toString
      fromRope == naiveStr.substring(0, alignedCount)
  }

  property("CharRope#drop is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr     = strings.mkString("")
      val alignedCount = normalizeOffset(count, naiveStr.length)
      val fromRope =
        ropeFromStrings(strings).characters.drop(alignedCount).toString
      fromRope == naiveStr.substring(alignedCount, naiveStr.length)
  }

  property("CodePointRope#take is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr = strings.mkString("")
      val offset =
        normalizeOffset(count, naiveStr.codePointCount(0, naiveStr.length))
      val fromRope = ropeFromStrings(strings).codePoints.take(offset).toString
      val fromString =
        naiveStr.substring(0, naiveStr.offsetByCodePoints(0, offset))
      fromRope == fromString
  }

  property("CodePointRope#drop is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr = strings.mkString("")
      val offset =
        normalizeOffset(count, naiveStr.codePointCount(0, naiveStr.length))
      val fromRope = ropeFromStrings(strings).codePoints.drop(offset).toString
      val fromString = naiveStr.substring(
        naiveStr.offsetByCodePoints(0, offset),
        naiveStr.length
      )
      fromRope == fromString
  }

  property("CharRope#split is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr = strings.mkString("")
      val offset   = normalizeOffset(count, naiveStr.length)
      val (fromRopeL, fromRopeR) =
        ropeFromStrings(strings).characters.splitAt(offset)
      val fromRope = (fromRopeL.toString, fromRopeR.toString)
      val fromString = (
        naiveStr.substring(0, offset),
        naiveStr.substring(offset, naiveStr.length)
      )
      fromRope == fromString
  }

  property("CodePointRope#split is consistent with strings") = forAll {
    (strings: List[String], count: Int) =>
      val naiveStr = strings.mkString("")
      val offset =
        normalizeOffset(count, naiveStr.codePointCount(0, naiveStr.length))
      val (fromRopeL, fromRopeR) =
        ropeFromStrings(strings).codePoints.splitAt(offset)
      val fromRope   = (fromRopeL.toString, fromRopeR.toString)
      val splitPoint = naiveStr.offsetByCodePoints(0, offset)
      val fromString = (
        naiveStr.substring(0, splitPoint),
        naiveStr.substring(splitPoint, naiveStr.length)
      )
      fromRope == fromString
  }


}
