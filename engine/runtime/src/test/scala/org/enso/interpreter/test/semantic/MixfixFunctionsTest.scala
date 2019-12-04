package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class MixfixFunctionsTest extends InterpreterTest {
  val subject = "Mixfix functions"

  subject should "be able to be defined as a method" in {
    val code =
      """
        |type Foo
        |
        |Foo.if_then = cond do -> 
        |""".stripMargin
  }

}
