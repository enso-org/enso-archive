package org.enso.textbuffer.editing

import org.enso.textbuffer.buffer.Rope

object TestData {

  val code =
    """
      |main =
      |    apply = v f -> f v
      |    adder = a b -> a + b
      |    plusOne = apply (f = adder 1)
      |    result = plusOne 10
      |    result""".stripMargin

  val testSnippet = Rope(code)

}
