package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class ListLiteralsTest extends InterpreterTest {
  "foo" should "bar" in {
    val code =
      """
        |main = IO.println [1, 2, 3]
        |""".stripMargin
    eval(code)
  }
}
