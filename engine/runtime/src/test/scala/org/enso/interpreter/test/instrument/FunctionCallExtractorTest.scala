package org.enso.interpreter.test.instrument
import org.enso.interpreter.test.InterpreterTest

class FunctionCallExtractorTest extends InterpreterTest {
  "it" should "work" in {
    val code =
      """
        |foo = x ->
        |    y = x + 1
        |    z = y + 1
        |    r = here.bar z
        |    r
        |
        |bar = y ->
        |    z = y + 5
        |    w = z * 5
        |    w
        |
        |main =
        |    y = here.foo 1234
        |    z = here.bar y
        |    IO.println z
        |    0
        |""".stripMargin
    getFunctionCallExtractorInstrument.bindTo({ x =>
      println(x)
    })
    eval(code)
    println(consumeOut)

  }
}
