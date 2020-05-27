package org.enso.compiler.test.pass.desugar

import org.enso.compiler.Passes
import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.desugar.{FunctionBinding, NestedPatternMatch}
import org.enso.compiler.test.CompilerTest

class NestedPatternMatchTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes

  val precursorPasses: List[IRPass] =
    passes.getPrecursors(NestedPatternMatch).get
  val passConfig: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfig)

  /** Adds an extension method to run nested pattern desugaring on an
    * [[IR.Module]].
    *
    * @param ir the module to run desugaring on
    */
  implicit class DesugarModule(ir: IR.Module) {

    /** Runs desugaring on a module.
      *
      * @param moduleContext the module context in which desugaring is taking
      *                      place
      * @return [[ir]], with any nested patterns desugared
      */
    def desugar(implicit moduleContext: ModuleContext): IR.Module = {
      NestedPatternMatch.runModule(ir, moduleContext)
    }
  }

  /** Adds an extension method to run nested pattern desugaring on an arbitrary
    * expression.
    *
    * @param ir the expression to desugar
    */
  implicit class DesugarExpression(ir: IR.Expression) {

    /** Runs desgaring on an expression.
      *
      * @param inlineContext the inline context in which the desugaring is
      *                      taking place
      * @return [[ir]], with nested patterns desugared
      */
    def desugar(implicit inlineContext: InlineContext): IR.Expression = {
      NestedPatternMatch.runExpression(ir, inlineContext)
    }
  }

  /** Creates a defaulted module context.
    *
    * @return a defaulted module context
    */
  def mkModuleContext: ModuleContext = {
    ModuleContext()
  }

  /** Creates a defaulted inline context.
    *
    * @return a defaulted inline context
    */
  def mkInlineContext: InlineContext = {
    InlineContext()
  }

  // === The Tests ============================================================

  "Nested pattern desugaring on modules" should {
    "desugar nested patterns to simple patterns" in {
      pending
    }

    "desugar deeply nested patterns to simple pattersn" in {
      pending
    }

    "work recursively" in {
      pending
    }
  }

  "Nested pattern desugaring on expressions" should {
    "desugar nested patterns to simple patterns" in {
      pending
    }

    "desugar deeply nested patterns to simple patterns" in {
      pending
    }

    "work recursively" in {
      pending
    }
  }
}
