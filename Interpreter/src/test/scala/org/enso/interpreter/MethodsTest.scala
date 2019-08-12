package org.enso.interpreter

class MethodsTest extends LanguageTest {
  "Methods" should "be defined in the global scope and dispatched to" in {
    val code =
      """
        |type Foo;
        |Foo.bar = { |number| number + 1 }
        |@bar [@Foo, 10]
        |""".stripMargin
    eval(code) shouldEqual 11
  }

  "Methods" should "be dispatched to the proper constructor" in {
    val code =
      """
        |Nil.sum = { |acc| acc }
        |Cons.sum = { |acc| match this <
        |  Cons ~ { |h, t| @sum [t, h + acc] };
        |>}
        |
        |@sum [@Cons [1, @Cons [2, @Nil]], 0]
        |""".stripMargin

    eval(code) shouldEqual 3
  }
}
