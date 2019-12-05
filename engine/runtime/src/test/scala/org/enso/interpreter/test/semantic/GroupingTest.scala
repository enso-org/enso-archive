package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class GroupingTest extends InterpreterTest {
  val condition = "be able to be grouped"

  "Arbitrary lambdas" should condition in {
    val code =
      """
        |(x -> x)
        |""".stripMargin

    eval(code).call(5) shouldEqual 5
  }

  "RHS of an assignment" should condition in {
    val code =
      """
        |fn = (x -> x)
        |
        |fn 10
        |""".stripMargin

    eval(code) shouldEqual 10
  }

}
