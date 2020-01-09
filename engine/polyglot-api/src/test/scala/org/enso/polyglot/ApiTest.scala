package org.enso.polyglot
import org.graalvm.polyglot.Context
import org.scalatest.{FlatSpec, Matchers}

class ApiTest extends FlatSpec with Matchers {
  import LanguageInfo._
  val executionContext = new ExecutionContext(Context.newBuilder(ID).build())

  "Parsing a file and calling a toplevel function defined in it" should "be possible" in {
    val code =
      """
        |foo = x -> x + 1
        |bar = x -> here.foo x + 1
        |""".stripMargin
    val module                = executionContext.evalModule(code, "Test")
    val associatedConstructor = module.getAssociatedConstructor
    val barFunction           = module.getMethod(associatedConstructor, "bar")
    val result = barFunction.execute(
      associatedConstructor.newInstance(),
      10L.asInstanceOf[AnyRef]
    )
    result.asLong shouldEqual 12
  }

  "Parsing a file and calling a method on an arbitrary atom" should "be possible" in {
    val code =
      """
        |type Vector x y z
        |
        |Vector.squares = case this of
        |    Vector x y z -> Vector x*x y*y z*z
        |
        |Vector.sum = case this of
        |    Vector x y z -> x + y + z
        |
        |Vector.squareNorm = this.squares.sum
        |""".stripMargin
    val module     = executionContext.evalModule(code, "Test")
    val vectorCons = module.getConstructor("Vector")
    val squareNorm = module.getMethod(vectorCons, "squareNorm")
    val testVector = vectorCons.newInstance(
      1L.asInstanceOf[AnyRef],
      2L.asInstanceOf[AnyRef],
      3L.asInstanceOf[AnyRef]
    )
    val testVectorNorm = squareNorm.execute(testVector)
    testVectorNorm.asLong shouldEqual 14
  }
}
