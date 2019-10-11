package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class MethodsTest extends InterpreterTest {
  "Methods" should "be defined in the global scope and dispatched to" in {
    pending
    val code =
      """
        |type Foo;
        |Foo.bar = { |number| number + 1 }
        |@bar [@Foo, 10]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 11
  }

  "Methods" should "be dispatched to the proper constructor" in {
    pending
    val code =
      """
        |Nil.sum = { |acc| acc }
        |Cons.sum = { |acc| match this <
        |  Cons ~ { |h, t| @sum [t, h + acc] };
        |>}
        |
        |@sum [@Cons [1, @Cons [2, @Nil]], 0]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 3
  }

  "Method call target" should "be passable by-name" in {
    pending
    val code =
      """
        |Unit.testMethod = { |x, y, z| (x + y) + z }
        |@testMethod [x = 1, y = 2, this = @Unit, z = 3]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 6
  }

  "Calling a non-existent method" should "throw an exception" in {
    pending
    val code =
      """
        |@foo [7]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    the[PolyglotException] thrownBy eval(code) should have message "Object 7 does not define method foo."
  }
}
