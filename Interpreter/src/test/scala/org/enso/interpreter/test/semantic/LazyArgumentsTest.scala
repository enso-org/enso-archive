package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class LazyArgumentsTest extends InterpreterTest {
  val subject = "Lazy arguments"

  subject should "not get executed upfront" in {
    pending
    val code =
      """
        |@{
        |  foo = { |i, $x, $y| ifZero: [i, $x, $y] };
        |  @foo [1, (print: 1), (print: 2)]
        |}
        |""".stripMargin
    noException should be thrownBy parse(code)
    eval(code)
    consumeOut shouldEqual List("2")
  }

  subject should "work well with tail recursion" in {
    pending
    val code =
      """
        |@{
        |  if = { |c, $ifT, $ifF| ifZero: [c, $ifT, $ifF] };
        |  sum = { |c, acc| @if [c, acc, @sum [c-1, acc + c]] };
        |  res = @sum [10000, 0];
        |  res
        |}
        |""".stripMargin
    eval(code) shouldEqual 50005000
  }

  subject should "work in non-tail positions" in {
    pending
    val code =
      """
        |@{
        |  suspInc = { |$x| 1 + ($x) };
        |  res = @suspInc [@suspInc [10]];
        |  res
        |}
        |""".stripMargin

    val result = eval(code)
    result shouldEqual 12
  }

  subject should "work properly with method dispatch" in {
    pending
    val code =
      """
        |type Foo;
        |type Bar;
        |
        |Foo.method = { |$x| 10 }
        |Bar.method = { |x| 10 }
        |
        |@{
        |  @method [@Foo, (print: 1)];
        |  @method [@Bar, (print: 2)];
        |  @method [@Foo, (print: 3)]
        |}
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("2")
  }

  subject should "work properly with oversaturated arguments" in {
    pending
    val code =
      """
        |@{
        |  if = { |c, $ifT, $ifF| ifZero: [c, $ifT, $ifF] };
        |  foo = { |c| @if [c] };
        |  @foo [0, (print: 1), (print: 2)];
        |  @foo [1, (print: 3), (print: 4)]
        |}
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("1","4")
  }
}
