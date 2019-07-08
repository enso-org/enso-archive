package org.enso.interpreter

class GlobalScopeTest extends LanguageTest {
  "Functions" should "use values from the global scope in their bodies" in {
    val code =
      """
        |a = 10;
        |addTen = { |b| a + b };
        |
        |@addTen [5]
    """.stripMargin

    eval(code) shouldEqual 15
  }

  "Functions" should "be able to call other functions in scope" in {
    val code =
      """
        |adder = { |a, b| a + b };
        |
        |@{ |multiply|
        |  res = @adder [1, 2];
        |  doubled = res * multiply;
        |  doubled
        |} [2]
    """.stripMargin

    eval(code) shouldEqual 6
  }

  // A function is a first-class value
  "Functions" should "be able to be passed as values when in scope" in {
    val code =
      """
        |adder = { |a, b| a + b };
        |
        |binaryFn = { |a, b, function|
        |  result = @function [a, b];
        |  result
        |}
        |
        |@binaryFn [1, 2, adder]
    """.stripMargin

    eval(code) shouldEqual 3
  }

  // Recursive calls between functions in the global scope
  "Functions" should "be able to mutually recurse in the global scope" in {
    val code =
      """
        |fn1 = { |number|
        |  ifZero: [number % 3, number, @decrementCall [x]]
        |}
        |
        |decrementCall = { |number|
        |  res = number - 1;
        |  @fn1 res
        |}
        |
        |@fn1 [5]
    """.stripMargin

    eval(code) shouldEqual 3
  }

  "Functions" should "be able to mutually recurse in the global scope" in {
    val code =
      """
        |fn1 = { |number|
        |  ifZero: [number % 3, number, @decrementCall [x]]
        |}
        |
        |decrementCall = { |number|
        |  res = number - 1;
        |  @fn1 res
        |
        |fn1
      """.stripMargin

    eval(code).call(5) shouldEqual 3
  }

}
