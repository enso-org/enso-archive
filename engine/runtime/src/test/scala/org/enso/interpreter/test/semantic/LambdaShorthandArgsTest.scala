package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class LambdaShorthandArgsTest extends InterpreterTest {

  val subject = "Lambda shorthand arguments"

  subject should "work for simple applications" in {
    val code =
      """
        |main =
        |    f = a -> b -> c -> a + b - c
        |    g = f _ 5 5
        |    g 10
        |""".stripMargin

    eval(code) shouldEqual 10
  }

  subject should "work for named applications" in {
    val code =
      """
        |main =
        |    f = a -> b -> a - b
        |    g = f (b = _)
        |    g 10 5
        |""".stripMargin

    eval(code) shouldEqual -5
  }

  subject should "work for functions in applications" in {
    val code =
    """
        |main =
        |    add = a -> b -> a + b
        |    sub = a -> b -> a - b
        |    f = _ 10 5
        |    res1 = f add
        |    res2 = f sub
        |    res1 - res2
        |""".stripMargin

    eval(code) shouldEqual 10
  }

  subject should "work with mixfix functions" in {
    val code =
      """
        |Number.if_then_else = ~t -> ~f -> ifZero this t f
        |
        |main =
        |    f = if _ then 10 else 5
        |    res1 = f 0
        |    res2 = f 1
        |    res1 - res2
        |""".stripMargin

    eval(code) shouldEqual 5
  }

  subject should "work with case expressions" in {
    val code =
    """
        |main =
        |    f = case _ of
        |           Cons a b -> 10
        |           Nil -> 0
        |    res1 = f (Cons 1 2)
        |    res2 = f Nil
        |    res2 - res1
        |""".stripMargin

    eval(code) shouldEqual -10
  }
}
