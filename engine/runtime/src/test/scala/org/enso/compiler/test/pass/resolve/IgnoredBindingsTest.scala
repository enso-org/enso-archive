package org.enso.compiler.test.pass.resolve

import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  OperatorToFunction,
  SectionsToBinOp
}
import org.enso.compiler.pass.resolve.IgnoredBindings
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class IgnoredBindingsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes: List[IRPass] = List(GenerateMethodBodies)

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
      IgnoredBindings.runExpression(ir, inlineContext)
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

  "Ignored bindings desugaring for function args" should {
    "replace ignored arguments with fresh names" in {
      pending
    }

    "mark ignored arguments as ignored" in {
      pending
    }

    "mark normal arguments as not ignored" in {
      pending
    }
  }

  "Ignored bindings desugaring for bindings" should {
    "replace the ignored binding with a fresh name" in {
      pending
    }

    "mark the binding as ignored if it was" in {
      pending
    }

    "mark the binding as not ignored if it wasn't" in {
      pending
    }
  }
}
