package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class PatternMatchTest extends InterpreterTest {

  // === Test Setup ===========================================================
  val subject = "Pattern matching"

  // === The Tests ============================================================

  subject should "work for simple patterns" in {
    val code =
      """
        |main =
        |    f = case _ of
        |        Cons a _ -> a
        |        Nil -> -10
        |
        |    (10.Cons Nil . f) - Nil.f
        |""".stripMargin

    eval(code) shouldEqual 20
  }

  subject should "work for anonymous catch-all patterns" in {
    val code =
      """
        |type MyAtom a
        |
        |main =
        |    f = case _ of
        |        MyAtom a -> a
        |        _ -> -100
        |
        |    (50.MyAtom . f) + Nil.f
        |""".stripMargin

    eval(code) shouldEqual -50
  }

  subject should "work for named catch-all patterns" in {
    val code =
      """
        |type MyAtom a
        |
        |main =
        |    f = case _ of
        |        MyAtom a -> a
        |        a -> a + 5
        |
        |    (50.MyAtom . f) + 30.f
        |""".stripMargin

    eval(code) shouldEqual 85
  }

  subject should "work for level one nested patterns" in {
    pending
    val code =
      """
        |type MyAtom
        |
        |main =
        |    f = a -> case a of
        |        Cons MyAtom _ -> 30
        |        _ -> -30
        |
        |    f (Cons MyAtom Nil)
        |""".stripMargin

    eval(code) shouldEqual 30
  }

  subject should "work for deeply nested patterns" in {
    pending
  }
}
