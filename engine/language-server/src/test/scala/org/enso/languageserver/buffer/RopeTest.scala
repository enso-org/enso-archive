package org.enso.languageserver.buffer
import java.util.stream.Collectors

import org.enso.languageserver.data.buffer.StringRope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class RopeTest extends AnyFlatSpec with Matchers {
  "StringRope" should "correctly concat strings" in {
    val rope = StringRope("hello world") ++ (StringRope(" it's a") ++
      StringRope(" pleasure") ++
      StringRope(" to meet you."))
    rope.toString shouldEqual "hello world it's a pleasure to meet you."
  }

  val rope = StringRope("𠜎a") ++ StringRope("𠜱bcą") ++ StringRope("ś𠝹łęk")

  "StringRope" should "allow splitting by code points" in {
    val (left, right) = rope.codePoints.splitAt(4)

    left.toString shouldEqual "𠜎a𠜱b"
    right.toString shouldEqual "cąś𠝹łęk"
  }

  "StringRope" should "allow taking substrings by code points" in {
    rope.codePoints.substring(1, 8).toString shouldEqual "a𠜱bcąś𠝹"
  }

  "StringRope" should "allow indexing by code points" in {
    rope.codePoints.get(7) shouldEqual "𠝹".codePointAt(0)
  }

  "StringRope" should "allow splitting by characters" in {
    val (left, right) = rope.characters.splitAt(6)

    left.toString shouldEqual "𠜎a𠜱b"
    right.toString shouldEqual "cąś𠝹łęk"
  }

  "StringRope" should "allow taking substrings by characters" in {
    rope.characters.substring(2, 11).toString shouldEqual "a𠜱bcąś𠝹"
  }

  "StringRope" should "allow indexing by characters" in {
    rope.characters.charAt(11) shouldEqual 'ł'
  }

  "CharRope" should "constitute a valid Java CharSequence" in {
    val codePointsViaRope =
      rope.characters.codePoints().iterator().asScala.toList

    val hardcodedPoints =
      "𠜎a𠜱bcąś𠝹łęk".codePoints().iterator().asScala.toList

    codePointsViaRope shouldEqual hardcodedPoints
  }

}
