package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.resolve.{IgnoredBindings, TypeFunctions}
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class TypeFunctionsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes

  val precursorPasses: List[IRPass] = passes.getPrecursors(TypeFunctions).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfiguration)

  /** Adds an extension method to resolve typing functions to an expression.
   *
   * @param ir the expression to resolve typing functions in
   */
  implicit class ResolveExpression(ir: IR.Expression) {

    /** Resolves typing functions on [[ir]].
     *
     * @param inlineContext the context win which resolution takes place
     * @return [[ir]], with typing functions resolved
     */
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      TypeFunctions.runExpression(ir, inlineContext)
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

  "Type functions resolution" should {
    "work for saturated applications" in {
      pending
    }

    "work for sections" in {
      pending
    }

    "work for underscore arguments" in {
      pending
    }
  }

  "Resolution" should {
    "resolve type ascription" in {
      pending
    }

    "resolve context ascription" in {
      pending
    }

    "resolve error ascription" in {
      pending
    }

    "resolve subsumption" in {
      pending
    }

    "resolve equality" in {
      pending
    }

    "resolve concatenation" in {
      pending
    }

    "resolve union" in {
      pending
    }

    "resolve intersection" in {
      pending
    }

    "resolve subtraction" in {
      pending
    }
  }
}
