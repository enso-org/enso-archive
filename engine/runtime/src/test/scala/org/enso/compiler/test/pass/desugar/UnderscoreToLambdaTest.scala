package org.enso.compiler.test.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.{GenerateMethodBodies, OperatorToFunction, SectionsToBinOp, UnderscoreToLambda}
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

  "Desugaring of underscore arguments" should {
    "Work for simple applications with underscore args" in {
      pending
    }

    "Work for named applications of underscore args" in {
      pending
    }

    "Work for an underscore scrutinee in a case expression" in {
      pending
    }

    "Work for method application with an underscore target" in {
      pending
    }
  }
}
