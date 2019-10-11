package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class InteropTest extends InterpreterTest {
  "Interop library" should "support tail recursive functions" in {
    pending
    val code =
      """
        |@{
        |  recurFun = { |i| ifZero: [i, 0, @recurFun [i - 1]] };
        |  recurFun
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    val recurFun = eval(code)
//    recurFun.call(15) shouldEqual 0
  }

  "Interop library" should "support calling curried functions" in {
    pending
    val code =
      """
        |@{
        |  fun = { |x, y, z| (x + y) + z };
        |  @fun [y = 1]
        |}
        |""".stripMargin

    noException should be thrownBy parse(code)
//    val curriedFun = eval(code)
//    curriedFun.call(2, 3) shouldEqual 6
  }

  "Interop library" should "support creating curried calls" in {
    pending
    val code =
      """
        |{ |x, y, z| (x + y) + z }
        |""".stripMargin

    noException should be thrownBy parse(code)
//    val fun = eval(code)
//    fun.call(1).call(2).call(3) shouldEqual 6
  }
}
