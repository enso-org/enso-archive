package org.enso.languageserver.text

import org.enso.languageserver.data.buffer.Rope
import org.enso.languageserver.text.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class RopeTextEditValidatorSpec extends AnyFlatSpec with Matchers {

  "A rope text editor" should "fail when end position is before start position" in {
    val buffer = Rope("1234567\nabcdefg")
    val diff1  = TextEdit(Range(Position(0, 3), Position(0, 2)), "a")
    RopeTextEditValidator.validate(buffer, diff1) mustBe Left(
      EndPositionBeforeStartPosition
    )
    val diff2 = TextEdit(Range(Position(0, 3), Position(0, 2)), "a")
    RopeTextEditValidator.validate(buffer, diff2) mustBe Left(
      EndPositionBeforeStartPosition
    )
  }

}
