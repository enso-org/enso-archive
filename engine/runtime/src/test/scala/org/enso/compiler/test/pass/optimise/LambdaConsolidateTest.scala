package org.enso.compiler.test.pass.optimise

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.desugar.{GenerateMethodBodies, LiftSpecialOperators, OperatorToFunction}
import org.enso.compiler.pass.optimise.LambdaConsolidate
import org.enso.compiler.test.CompilerTest

class LambdaConsolidateTest extends CompilerTest {

  // === Test Setup ===========================================================

  val precursorPasses: List[IRPass] = List(
    GenerateMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis
  )

  val passConfiguration = new PassConfiguration(
    Map(AliasAnalysis -> AliasAnalysis.Configuration())
  )

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfiguration)

  /** Adds an extension method to run lambda consolidation on an [[IR.Module]].
   *
   * @param ir the module to run lambda consolidation on
   */
  implicit class OptimiseModule(ir: IR.Module) {

    /** Runs lambda consolidation on a module.
     *
     * @return [[ir]], with chained lambdas consolidated
     */
    def optimise: IR.Module = {
      LambdaConsolidate.runModule(
        ir,
        ModuleContext(passConfiguration = Some(passConfiguration))
      )
    }
  }

  /** Adds an extension method to run lambda consolidation on an
   * [[IR.Expression]].
   *
   * @param ir the expression to run lambda consolidation on
   */
  implicit class OptimiseExpression(ir: IR.Expression) {

    /** Runs lambda consolidation on an expression.
     *
     * @param inlineContext the inline context in which to process the
     *                      expression
     * @return [[ir]], with chained lambdas consolidated
     */
    def optimise(inlineContext: InlineContext): IR.Expression = {
      LambdaConsolidate.runExpression(ir, inlineContext)
    }
  }

  // === The Tests ============================================================

  "Lambda consolidation" should {

    "collapse chained lambdas into a single lambda" in {
      pending
    }

    "rename shadowed parameters" in {
      pending
    }

    "work properly with default arguments" in {
      pending
    }

    "output a warning when lambda chaining shadows a parameter definition" in {
      pending
    }

    "maintain laziness of collapsed parameters" in {
      pending
    }

    "do a thing" in {
      val ir =
        """
          |a -> (b = a) -> a + b
          |""".stripMargin.toIrExpression.get

//      println(ir.pretty)
    }
  }
}
