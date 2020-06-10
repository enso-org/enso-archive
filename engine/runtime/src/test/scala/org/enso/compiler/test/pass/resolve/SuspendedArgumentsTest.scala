package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.resolve.SuspendedArguments
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class SuspendedArgumentsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes

  val precursorPasses: List[IRPass] =
    passes.getPrecursors(SuspendedArguments).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfiguration)

  /** Adds an extension method to a module for performing suspended argument
    * resolution.
    *
    * @param ir the IR to add the extension method to
    */
  implicit class ResolveModule(ir: IR.Module) {

    /** Resolves suspended arguments in [[ir]].
      *
      * @param moduleContext the context in which resolution is taking place
      * @return [[ir]], with all suspended arguments resolved
      */
    def resolve(implicit moduleContext: ModuleContext): IR.Module = {
      SuspendedArguments.runModule(ir, moduleContext)
    }
  }

  /** Adds an extension method to an expression for performing suspended
    * argument resolution.
    *
    * @param ir the expression to add the extension method to
    */
  implicit class ResolveExpression(ir: IR.Expression) {

    /** Resolves suspended arguments in [[ir]].
      *
      * @param inlineContext the context in which resolution is taking place
      * @return [[ir]], with all suspended arguments resolved
      */
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      SuspendedArguments.runExpression(ir, inlineContext)
    }
  }

  /** Creates a defaulted module context.
    *
    * @return a defaulted module context
    */
  def mkModuleContext: ModuleContext = {
    ModuleContext(freshNameSupply = Some(new FreshNameSupply))
  }

  /** Creates a defaulted inline context.
    *
    * @return a defaulted inline context
    */
  def mkInlineContext: InlineContext = {
    InlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  "Suspended arguments resolution in modules" should {
    "correctly mark arguments as suspended based on their type signatures" in {
      pending
    }
  }

  "Suspended arguments resolution in expressions" should {
    "correctly mark arguments as suspended in blocks" in {
      pending
    }

    "correctly mark arguments as suspended using inline expressions" in {
      // TODO `x -> x : a -> a`
      pending
    }
  }

}
