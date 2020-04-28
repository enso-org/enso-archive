package org.enso.compiler.test.pass.optimise

import org.enso.compiler.context.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  LiftSpecialOperators,
  OperatorToFunction
}
import org.enso.compiler.test.CompilerTest

class LambdaConsolidateTest extends CompilerTest {
  trait Foo extends IR.Metadata
  case class Bar(a: Int) extends Foo {
    override val metadataName: String = "Bar"
  }
  case class Baz(a: Int) extends Foo {
    override val metadataName: String = "Baz"
  }

  case class Quux(a: Int) extends IR.Metadata {
    override val metadataName: String = "Quux"
  }

  "thing" should {
    "thingy" in {
      implicit val inlineContext: InlineContext = InlineContext()

      val passes: List[IRPass] = List(
        GenerateMethodBodies,
        LiftSpecialOperators,
        OperatorToFunction,
        OperatorToFunction,
        GenerateMethodBodies,
        GenerateMethodBodies
      )

      val passConfig = new PassConfiguration

      implicit val passManager: PassManager =
        new PassManager(passes, passConfig)

      """
        |x -> x
        |""".stripMargin.preprocessExpression
    }
  }

}
