package org.enso.compiler.test.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  OperatorToFunction,
  SectionsToBinOp,
  UnderscoreToLambda
}
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class UnderscoreToLambdaTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes: List[IRPass] = List(
    GenerateMethodBodies,
    SectionsToBinOp,
    OperatorToFunction
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(passes, passConfiguration)

  /** Adds an extension method for running desugaring on the input IR.
    *
    * @param ir the IR to desugar
    */
  implicit class DesugarExpression(ir: IR.Expression) {

    /** Runs section desugaring on [[ir]].
      *
      * @param inlineContext the inline context in which the desugaring takes
      *                      place
      * @return [[ir]], with all sections desugared
      */
    def desugar(implicit inlineContext: InlineContext): IR.Expression = {
      UnderscoreToLambda.runExpression(ir, inlineContext)
    }
  }

  /** Makes an inline context.
    *
    * @return a new inline context
    */
  def mkInlineContext: InlineContext = {
    InlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  // TODO [AA] Test all these with runtime e2e tests too

  "Desugaring of underscore arguments" should {
    "Work for simple applications with underscore args" in {
      // TODO [AA] Make sure the args end up in the right order
      pending
    }

    "Work for named applications of underscore args" in {
      // TODO [AA] check arg ordering in this case too
      pending
    }

    "Work if the function in an application is an underscore arg" in {
      // TODO [AA] Make sure this is ordered in the outermost lambda
      pending
    }

    "Work with mixfix functions" in {
      pending
    }

    "Work for an underscore scrutinee in a case expression" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |case _ of
          |    Nil -> 0
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]

      val irLam = ir.asInstanceOf[IR.Function.Lambda]
      irLam.arguments.length shouldEqual 1

      val lamArgName =
        irLam.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      val lamBody = irLam.body.asInstanceOf[IR.Case.Expr]

      lamBody.scrutinee shouldBe an[IR.Name.Literal]
      lamBody.scrutinee
        .asInstanceOf[IR.Name.Literal]
        .name shouldEqual lamArgName.name
    }
  }

  "Nested underscore arguments" should {
    "work for applications" in {
      pending
    }

    "work for named applications" in {
      pending
    }

    "work for function name underscores" in {
      pending
    }

    "work for case expressions" in {
      // TODO [AA] check in branches and fallback
      pending
    }
  }
}
