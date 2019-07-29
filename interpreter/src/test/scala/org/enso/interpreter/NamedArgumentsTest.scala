package org.enso.interpreter

class NamedArgumentsTest extends LanguageTest {
  "Functions" should "take arguments by name and use them in their bodies" in {

    val code =
      """
        |a = 10
        |addTen = { |b| a + b }
        |
        |@addTen [b = 10]
      """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 2
  }

  "Functions" should "be able to have named arguments given out of order" in {
    val code =
      """
        |subtract = { |a, b| a - b }
        |
        |@subtract [b = 10, a = 5]
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual -5
  }

  "Functions" should "be able to have scope values as named arguments" in {
    val code =
      """
        |a = 10
        |addTen = { |num| num + a }
        |
        |@addTen [num = a]
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 20
  }

  "Functions" should "be able to be defined with default argument values" in {
    val code =
      """
        |addNum = { |a, num = 10| a + num }
        |
        |@addNum [5]
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 15
  }

  "Default arguments" should "not have other function arguments in scope" in {
    val code =
    """
        |doThingAndAdd = { |a, b = a + a| a + b }
        |
        |@doThingAndAdd [1]
        |""".stripMargin

    noException should be thrownBy parse(code)
    // TODO [AA] The function parameters should not be in scope for defaults
//    the[PolyglotException] thrownBy eval(code) should have message ""
  }

  "Default arguments" should "be able to default to complex expressions" in {
    val code =
      """
        |add = { |a, b| a + b }
        |
        |doThing = { |a, b = @add [1, 2]| a + b }
        |
        |@doThing [10]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldBe 13
  }

  "Default arguments" should "be able to close over their outer scope" in {
    val code =
    """
        |id = { |x| x }
        |
        |apply = { |val, fn = id| @id [val] }
        |
        |@apply [val = 1]
        |""".stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 1
  }

  "Functions" should "use their default values when none is supplied" in {
    val code =
      """
        |addTogether = { |a = 5, b = 6| a + b }
        |
        |@addTogether
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 11
  }

  "Functions" should "allow defaulted values to be removed" in {
    val code =
      """
        |addNum = { |a, num = 10| a + num }
        |
        |add = @addNum [num = !!]
        |
        |0
      """.stripMargin

    noException should be thrownBy parse(code)
    // TODO [AA] This needs currying to be tested properly. Just test parse for
    // now.
//    eval(code) shouldEqual 0
  }

  "Functions" should "override defaults by name" in {
    val code =
      """
        |addNum = { |a, num = 10| a + num }
        |
        |@addNum [1, num = 1]
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 2
  }

  "Functions" should "override defaults by position" in {
    val code =
      """
        |addNum = { |a, num = 10| a + num }
        |
        |@addNum [1, 2]
    """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 3
  }

}
