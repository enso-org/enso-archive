package org.enso.languageserver.buffer
import org.enso.languageserver.data.buffer.Rope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RopeTest extends AnyFlatSpec with Matchers {
  "Rope" should "allow concatenating and indexing strings" in {
    val myRope = Rope("012") ++ Rope("345") ++ Rope("678")
    myRope(1) shouldEqual Some('1')
    myRope(3) shouldEqual Some('3')
    myRope(8) shouldEqual Some('8')
  }
}
