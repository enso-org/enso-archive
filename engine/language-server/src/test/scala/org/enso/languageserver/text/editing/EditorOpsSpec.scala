package org.enso.languageserver.text.editing

import org.enso.languageserver.text.editing.TestData.testSnippet
import org.enso.languageserver.text.editing.model.{Position, Range, TextEdit}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class EditorOpsSpec extends AnyFlatSpec with Matchers with EitherValues {

  "An editor" should "be able to apply multiple diffs" in {
    //given
    val signaturePosition = Range(Position(2, 12), Position(2, 13))
    val signatureDiff     = TextEdit(signaturePosition, "arg")
    val bodyPosition      = Range(Position(2, 23), Position(2, 24))
    val bodyDiff          = TextEdit(bodyPosition, "arg")
    val diffs             = List(signatureDiff, bodyDiff)
    //when
    val result = EditorOps.applyEdits(testSnippet, diffs)
    //then
    result.map(_.toString) mustBe Right("""
                                          |main =
                                          |    apply = arg f -> f arg
                                          |    adder = a b -> a + b
                                          |    plusOne = apply (f = adder 1)
                                          |    result = plusOne 10
                                          |    result""".stripMargin)
  }

}
