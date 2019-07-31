package org.enso.interpreter

import org.graalvm.polyglot.PolyglotException

class FunctionArgumentsTest extends LanguageTest {
  "Functions" should "take arguments and use them in their bodies" in {
    val code     = "{ |x| x * x }"
    val function = eval(code)
    function.call(1) shouldEqual 1
    function.call(4) shouldEqual 16
  }

  "Function arguments from outer scope" should "be visible in the inner scope" in {
    val code =
      """
        |{ |a|
        |  adder = { |b| a + b };
        |  res = @adder [2];
        |  res
        |}  
      """.stripMargin

    eval(code).call(3) shouldEqual 5
  }

  "Recursion" should "work" in {
    val code =
      """
        |@{ 
        |  sumTo = { |x| ifZero: [x, 0, x + (@sumTo [x - 1])] };
        |  @sumTo [10]
        |}
      """.stripMargin

    eval(code) shouldEqual 55
  }

  "Functions" should "not take more arguments than possible" in {
    val code =
      """
        |@{
        |  sumTo = { |x| ifZero: [x, 0, x + (@sumTo [x - 1])] };
        |  @sumTo [10, 11]
        |}
      """.stripMargin

    val errMsg =
      """
        |org.enso.interpreter.runtime.errors.ArityException: Wrong number of
        | arguments. Expected: 1 but got: 2.
        |""".stripMargin.replaceAll("\n", "")

    the[PolyglotException] thrownBy eval(code) should have message errMsg
  }

}
