package org.enso.compiler.test.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.desugar.{GenerateMethodBodies, SectionsToBinOp}
import org.enso.compiler.test.CompilerTest

class SectionsToBinOpTest extends CompilerTest {

  // === Test Configuration ===================================================

  val passes: List[IRPass] = List(
    GenerateMethodBodies
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(passes, passConfiguration)

  implicit class DesugarExpression(ir: IR.Expression) {

    def desugar(implicit inlineContext: InlineContext): IR.Expression = {
      SectionsToBinOp.runExpression(ir, inlineContext)
    }
  }

  def mkInlineContext: InlineContext = {
    InlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  "Operator section desugaring" should {
    "work for left sections" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |(1 +)
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Application.Prefix]
      ir.asInstanceOf[IR.Application.Prefix].arguments.length shouldEqual 1
    }

    "work for sides sections" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |(+)
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Application.Prefix]
      ir.asInstanceOf[IR.Application.Prefix].arguments.length shouldEqual 0
    }

    "work for right sections" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |(+ 1)
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]

      val irLam = ir.asInstanceOf[IR.Function.Lambda]
      irLam.arguments.length shouldEqual 1

      val lamArgName =
        irLam.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      val lamBody = irLam.body.asInstanceOf[IR.Application.Prefix]
      lamBody.arguments.length shouldEqual 2
      val lamBodyFirstArg =
        lamBody.arguments.head
          .asInstanceOf[IR.CallArgument.Specified]
          .value
          .asInstanceOf[IR.Name.Literal]

      lamBodyFirstArg.name shouldEqual lamArgName.name
      lamBodyFirstArg.getId should not equal lamArgName.getId
    }

    "work when the section is nested" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |x -> (x +)
          |""".stripMargin.preprocessExpression.get.desugar
          .asInstanceOf[IR.Function.Lambda]

      ir.body shouldBe an[IR.Application.Prefix]
      ir.body.asInstanceOf[IR.Application.Prefix].arguments.length shouldEqual 1
    }
  }
}
