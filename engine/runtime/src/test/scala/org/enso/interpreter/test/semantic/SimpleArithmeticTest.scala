package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class SimpleArithmeticTest extends InterpreterTest {
  "1" should "equal 1" in {
    evalOld("1") shouldEqual 1
  }

  "1 + 1" should "equal 2" in {
    evalOld("1 + 1") shouldEqual 2
  }

  "2 + (2 * 2)" should "equal 6" in {
    evalOld("2 + (2 * 2)") shouldEqual 6
  }

  "2 * 2 / 2" should "equal 2" in {
    evalOld("(2 * 2) / 2") shouldEqual 2
    // TODO [AA] Remove parens from the above
  }

  // TODO [AA] Non-parensed tests with new AST to check parens logic
}
