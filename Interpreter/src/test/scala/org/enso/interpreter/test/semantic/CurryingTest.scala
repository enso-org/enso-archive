package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class CurryingTest extends InterpreterTest {
  "Functions" should "allow partial application" in {
    pending
    val code =
      """
        |@{
        |  apply = { |v, f| @f [v] };
        |  adder = { |a, b| a + b };
        |  plusOne = @apply [f = @adder [1]];
        |  result = @plusOne [10];
        |  result
        |}
        |""".stripMargin
    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 11
  }

  "Functions" should "allow default arguments to be suspended" in {
    pending
    val code =
      """
        |@{
        |  fn = { |w, x, y = 10, z = 20| (w + x) + (y + z) };
        |
        |  fn1 = @fn ...;
        |  fn2 = @fn1 [1, 2] ...;
        |  fn3 = @fn2 [3] ...;
        |
        |  @fn3
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 26
  }

  "Functions" should "allow defaults to be suspended in application chains" in {
    pending
    val code =
      """
        |@{
        |  fn = { |w, x, y = 10, z = 20| (w + x) + (y + z) };
        |
        |  @(@fn [3, 6] ...) [3]
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 32
  }
}
