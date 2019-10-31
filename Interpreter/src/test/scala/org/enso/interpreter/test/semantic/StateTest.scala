package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.LanguageTest

import scala.util.Try

class StateTest extends LanguageTest {
  "State" should "be accessible from functions" in {
    val code =
      """
        |@{
        |  @put [@State, 10];
        |  x = @get [@State];
        |  @put [@State, x + 1];
        |  @get [@State]
        |}
        |""".stripMargin

    eval(code) shouldEqual 11
  }

  "State" should "be implicitly threaded through function executions" in {
    val code =
      """
        |Unit.incState = {
        |  x = @get [@State];
        |  @put [@State, x + 1]
        |}
        |
        |@{
        |  @put [@State, 0];
        |  @incState [@Unit];
        |  @incState [@Unit];
        |  @incState [@Unit];
        |  @incState [@Unit];
        |  @incState [@Unit];
        |  @get [@State]
        |}
        |""".stripMargin

    eval(code) shouldEqual 5
  }

  "State" should "work well with recursive code" in {
    val code =
      """
        |@{
        |  stateSum = { |n|
        |    acc = @get [@State];
        |    @println[@IO, acc];
        |    @put [@State, acc + n];
        |    ifZero: [n, @get [@State], @stateSum [n-1]]
        |  };
        |  @put [@State, 0];
        |  @stateSum [10]
        |}
        |""".stripMargin
    eval(code) shouldEqual 55
  }
}
