package org.enso.interpreter.test.instrument
import org.enso.interpreter.test.InterpreterTest

import scala.collection.mutable

class ValueExtractorTest extends InterpreterTest {
  "Value extractor" should "extract values in a simple expression" in {
    val code    = "main = 2 + 2"
    val results = mutable.HashMap[(Int, Int), Any]()
    getValueExtractorInstrument.bindTo(7, 5, { res =>
      results.put((7, 5), res)
    })
    eval(code)
    results shouldEqual mutable.HashMap((7,5) -> 4)
  }
}
