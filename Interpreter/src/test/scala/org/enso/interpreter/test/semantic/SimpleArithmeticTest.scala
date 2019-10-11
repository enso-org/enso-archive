package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class SimpleArithmeticTest extends InterpreterTest {
  "1 + 1" should "equal 2" in {
    pending

    noException should be thrownBy parse("1 + 1")
//    eval("1 + 1") shouldEqual 2
  }
  "2 + (2 * 2)" should "equal 6" in {
    pending

    noException should be thrownBy parse("2 + (2 * 2)")
//    eval("2 + (2 * 2)") shouldEqual 6
  }
}
