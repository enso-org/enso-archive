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
                             |    result""".stripMargin
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
                             |    result""".stripMargin
  }

  it should "replace a multiline substring" in {
    //given
    val resultPosition    = Range(Position(5, 4), Position(6, 10))
    val change            = "sum = plusOne 5\n    sum"
    val resultReplacement = TextEdit(resultPosition, change)
    //when
    val result = RopeTextEditor.edit(rope, resultReplacement)
    //then
    result.toString mustBe """
                             |main =
                             |    apply = v f -> f v
                             |    adder = a b -> a + b
                             |    plusOne = apply (f = adder 1)
                             |    sum = plusOne 5
                             |    sum""".stripMargin
  }

  it should "be able to insert change at the end of file" in {
    //given
    val eof       = Range(Position(6, 10), Position(6, 10))
    val insertion = TextEdit(eof, "\n    return result")
    //when
    val result = RopeTextEditor.edit(rope, insertion)
    //then
    result.toString mustBe """
                             |main =
                             |    apply = v f -> f v
                             |    adder = a b -> a + b
                             |    plusOne = apply (f = adder 1)
                             |    result = plusOne 10
                             |    result
                             |    return result""".stripMargin
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
      |    result""".stripMargin

  val rope = Rope(code)

}
