package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.LanguageTest

class SimpleArithmeticTest extends LanguageTest {
  "1 + 1" should "equal 2" in {
    pending
    eval("1 + 1") shouldEqual 2
  }
  "2 + (2 * 2)" should "equal 6" in {
    pending
    eval("2 + (2 * 2)") shouldEqual 6
  }
}
