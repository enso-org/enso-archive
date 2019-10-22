package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class FunctionArgumentsTest extends InterpreterTest {
  "Functions" should "take arguments and use them in their bodies" in {
    pending
    val code =
        """
        |{ |x| x * x }
        |""".stripMargin

    noException should be thrownBy parse(code)
//    val function = eval(code)
//    function.call(1) shouldEqual 1
//    function.call(4) shouldEqual 16
  }

  "Function arguments from outer scope" should "be visible in the inner scope" in {
    pending
    val code =
      """
        |{ |a|
        |  adder = { |b| a + b };
        |  res = @adder [2];
        |  res
        |}
      """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code).call(3) shouldEqual 5
  }

  "Recursion" should "work" in {
    pending
    val code =
      """
        |@{
        |  sumTo = { |x| ifZero: [x, 0, x + (@sumTo [x - 1])] };
        |  @sumTo [10]
        |}
      """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 55
  }

  "Function calls" should "accept more arguments than needed and pass them to the result upon execution" in {
    pending
    val code =
      """
        |@{
        |  f = { |x| { |z| x + z } };
        |
        |  @f [1, 2]
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 3
  }

  "Function calls" should "allow oversaturation and execute until completion" in {
    pending
    val code =
      """
        |@{
        |  f = { | x, y | { | w | { | z | (x * y) + (w + z) } } };
        |
        |  @f [3, 3, 10, 1]
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 20
  }

  "Function calls" should "be able to return atoms that are evaluated with oversaturated args" in {
    pending
    val code =
      """
        |@{
        |  f = { |x| Cons };
        |
        |  myCons = @f [1, 2, 3];
        |
        |  match myCons <
        |    Cons ~ { |h, t| h + t };
        |  >
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 5
  }

  "Methods" should "support the use of oversaturated args" in {
    pending
    val code =
      """
        |Unit.myMethod = 1
        |
        |@{
        |  f = { |x| myMethod };
        |  t = @f [10, @Unit];
        |
        |  t
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 1
  }

  "Recursion closing over lexical scope" should "work properly" in {
    pending
    val code =
      """
        |@{
        |  summator = { |current| ifZero: [current, 0,  @{@summator [current - 1]} ] };
        |  res = @summator [0];
        |  res
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 0
  }
}
