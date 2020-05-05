package org.enso.compiler.test.pass.lint

import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  LambdaShorthandToLambda
}
import org.enso.compiler.pass.lint.UnusedBindings
import org.enso.compiler.pass.resolve.IgnoredBindings
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class UnusedBindingsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes: List[IRPass] = List(
    GenerateMethodBodies,
    LambdaShorthandToLambda,
    IgnoredBindings
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(passes, passConfiguration)

  /** Adds an extension method for running linting on the input IR.
    *
    * @param ir the IR to lint
    */
  implicit class LintExpression(ir: IR.Expression) {

    /** Runs unused name linting on [[ir]].
      *
      * @param inlineContext the inline context in which the desugaring takes
      *                      place
      * @return [[ir]], with all unused names linted
      */
    def lint(implicit inlineContext: InlineContext): IR.Expression = {
      UnusedBindings.runExpression(ir, inlineContext)
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

  "Unused bindings linting" should {
    "attach a warning to an unused function argument" in {
      pending
    }

    "not attach a warning to an unused function argument if it is an ignore" in {
      pending
    }

    "attach a warning to an unused binding" in {
      pending
    }

    "not attach a warning to an unused binding if it is an ignore" in {
      pending
    }
  }
}
