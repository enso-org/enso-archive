package org.enso.interpreter.test.semantic

import org.enso.interpreter.node.ClosureRootNode
import org.enso.interpreter.node.callable.ApplicationNode
import org.enso.interpreter.node.callable.function.CreateFunctionNode
import org.enso.interpreter.node.scope.AssignmentNode
import org.enso.interpreter.test.InterpreterTest
import org.enso.polyglot.MethodNames

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
  withLocationsInstrumenter { _ =>
    val code =
      """
        |Test.foo a b = a * b - a
        |
        |main = Test.foo 2 3
        |""".stripMargin

//    instrumenter.assertNodeExists(1, 25, classOf[ClosureRootNode])
    val mod = executionContext.evalModule(code, "Test")
    val tpe = mod.getAssociatedConstructor
    val method = mod.getMethod(tpe, "foo")
    method.value.invokeMember(MethodNames.Function.GET_SOURCE_START) shouldEqual 1
    method.value.invokeMember(MethodNames.Function.GET_SOURCE_LENGTH) shouldEqual 24

    eval(code)
  }
}
