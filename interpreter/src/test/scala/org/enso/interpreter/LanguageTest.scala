package org.enso.interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.scalactic.Equality
import org.scalatest.FlatSpec
import org.scalatest.Matchers

abstract class LanguageTest extends FlatSpec with Matchers {
  val ctx = Context.newBuilder(Constants.LANGUAGE_ID).build()

  implicit val valueEquality: Equality[Value] = (a: Value, b: Any) =>
    b match {
      case _: Long => a.isNumber && a.fitsInLong && a.asLong == b
      case _: Int  => a.isNumber && a.fitsInInt && a.asInt == b
      case _       => false
    }

  def eval(code: String): Value = ctx.eval(Constants.LANGUAGE_ID, code)
}
