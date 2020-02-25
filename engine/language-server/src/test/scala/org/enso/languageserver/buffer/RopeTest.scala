package org.enso.languageserver.buffer
import org.enso.languageserver.data.buffer.StringRope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RopeTest extends AnyFlatSpec with Matchers {
  "StringRope" should "correctly concat strings" in {
    val rope = StringRope("hello world") ++ (StringRope(" it's a") ++
      StringRope(" pleasure") ++
      StringRope(" to meet you."))
    rope.toString shouldEqual "hello world it's a pleasure to meet you."
  }

  "StringRope" should "be splittable by code points" in {
    val rope          = StringRope("𠜎a") ++ StringRope("𠜱bcą") ++ StringRope("ś𠝹łęk")
    val (left, right) = rope.splitAtCodePoint(4)
    left.toString shouldEqual "𠜎a𠜱b"
    right.toString shouldEqual "cąś𠝹łęk"
  }
}
