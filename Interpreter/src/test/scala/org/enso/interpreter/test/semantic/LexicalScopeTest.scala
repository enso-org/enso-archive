package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class LexicalScopeTest extends InterpreterTest {
  "Scope capture from outer scope" should "work" in {
    pending
    val code =
      """
        |@{
        |  x = 10;
        |  @{
        |    y = x;
        |    y + 5
        |  }
        |}  
      """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 15
  }

  "Variable shadowing" should "work" in {
    pending
    val code =
      """
        |@{
        |  x = 10;
        |  @{
        |    x = 5;
        |    x + 1
        |  }
        |}
      """.stripMargin

    noException should be thrownBy parse(code)
//    eval(code) shouldEqual 6
  }

  "Variable redefinition in same scope" should "throw error" in {
    pending
    val code =
      """
        |@{ 
        |  x = 10;
        |  @{
        |    y = x;
        |    y = 5;
        |    y + 1
        |  }
        |}
      """.stripMargin

    noException should be thrownBy parse(code)
//    the[PolyglotException] thrownBy eval(code) should have message "Variable y was already defined in this scope."
  }

  "Reference to an undefined variable" should "throw error" in {
    //TODO [AA] Pending, because we're not yet sure what the behavior should be in the presence
    // of dynamic dispatch. `y` in this code is actually equivalent to `x -> x.y`.
    pending
    val code =
      """
        |@{
        |  x = 10;
        |  y
        |}
      """.stripMargin
    noException should be thrownBy parse(code)
//    the[PolyglotException] thrownBy eval(code) should have message "Variable y is not defined."
  }

}
