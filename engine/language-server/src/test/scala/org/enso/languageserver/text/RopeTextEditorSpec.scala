package org.enso.languageserver.text

import org.enso.languageserver.data.buffer.Rope
import org.enso.languageserver.text.RopeTextEditorSpec._
import org.enso.languageserver.text.model.{Position, Range, TextEdit}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class RopeTextEditorSpec extends AnyFlatSpec with Matchers {

  "A rope text editor" should "insert text before beginning of a line" in {
    //given
    val beforeMain          = Range(Position(1, 0), Position(1, 0))
    val insertionBeforeMain = TextEdit(beforeMain, "ultra_")
    //when
    val result = RopeTextEditor.edit(rope, insertionBeforeMain)
    //then
    result.toString mustBe """
                             |ultra_main =
                             |    apply = v f -> f v
                             |    adder = a b -> a + b
                             |    plusOne = apply (f = adder 1)
                             |    result = plusOne 10
                             |    result
                             |""".stripMargin
  }

  it should "replace a substring" in {
    //given
    val mainPosition    = Range(Position(1, 0), Position(1, 4))
    val mainReplacement = TextEdit(mainPosition, "run")
    //when
    val result = RopeTextEditor.edit(rope, mainReplacement)
    //then
    result.toString mustBe """
                             |run =
                             |    apply = v f -> f v
                             |    adder = a b -> a + b
                             |    plusOne = apply (f = adder 1)
                             |    result = plusOne 10
                             |    result
                             |""".stripMargin
  }

}

object RopeTextEditorSpec {

  val code =
    """
      |main =
      |    apply = v f -> f v
      |    adder = a b -> a + b
      |    plusOne = apply (f = adder 1)
      |    result = plusOne 10
      |    result
      |""".stripMargin

  val rope = Rope(code)

}
