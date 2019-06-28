package org.enso.interpreter

class SimpleArithmeticTest extends LanguageTest {
  "1 + 1" should "equal 2" in {
    eval("1 + 1") shouldEqual 2
  }
}
