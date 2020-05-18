package org.enso.interpreter.test.semantic

import org.enso.interpreter.node.ClosureRootNode
import org.enso.interpreter.node.callable.ApplicationNode
import org.enso.interpreter.node.callable.function.CreateFunctionNode
import org.enso.interpreter.node.scope.AssignmentNode
import org.enso.interpreter.test.InterpreterTest

class FunctionSugarTest extends InterpreterTest {

  "Sugared function definitions" should "work" in {
    val code =
      """
        |main =
        |    f a b = a - b
        |    f 10 20
        |""".stripMargin

    eval(code) shouldEqual -10
  }

  "Sugared function definitions" should "get the right locations" in
  withLocationsInstrumenter { instrumenter =>
    val code =
      """
        |main =
        |    f a b = a - b
        |    f 10 20
        |""".stripMargin

    instrumenter.assertNodeExists(12, 13, classOf[AssignmentNode])
    eval(code)
  }

  "Sugared method definitions" should "work" in {
    val code =
      """
        |Unit.foo a b = a * b - a
        |
        |main = Unit.foo 2 3
        |""".stripMargin

    eval(code) shouldEqual 4
  }

  "Sugared method definitions" should "get the right locations" in
  withLocationsInstrumenter { instrumenter =>
    val code =
      """
        |Unit.foo a b = a * b - a
        |
        |main = Unit.foo 2 3
        |""".stripMargin

    instrumenter.assertNodeExists(1, 24, classOf[ClosureRootNode])
    eval(code)
  }
}
