package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** This pass translates ignored bindings (of the form `_`) into fresh names
  * internally, as well as marks all bindings as whether or not they were
  * ignored.
  *
  * It depends on [[org.enso.compiler.pass.desugar.GenerateMethodBodies]].
  */
case object IgnoredBindings extends IRPass {
  override type Metadata = State
  override type Config   = IRPass.Configuration.Default

  /** Desugars ignored bindings for a module.
   *
   * @param ir the Enso IR to process
   * @param moduleContext a context object that contains the information needed
   *                      to process a module
   * @return `ir`, possibly having made transformations or annotations to that
   *         IR.
   */
  override def runModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = ir.transformExpressions {
    case x =>
      x.mapExpressions(
        runExpression(
          _,
          InlineContext(freshNameSupply = moduleContext.freshNameSupply)
        )
      )
  }

  /** Desugars ignored bindings for an arbitrary expression.
   *
   * @param ir the Enso IR to process
   * @param inlineContext a context object that contains the information needed
   *                      for inline evaluation
   * @return `ir`, possibly having made transformations or annotations to that
   *         IR.
   */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val freshNameSupply = inlineContext.freshNameSupply.getOrElse(
      throw new CompilerError(
        "Desugaring underscore arguments to lambdas requires a fresh name " +
        "supply."
      )
    )

    desugarExpression(ir, freshNameSupply)
  }

  // === Pass Internals =======================================================

  /** Desugars ignored bindings of the form `_` in an arbitrary expression.
   *
   * @param expression the expression to perform desugaring on
   * @param supply the compiler's fresh name supply
   * @return `expression`, with any ignored bidings desugared
   */
  private def desugarExpression(
    expression: IR.Expression,
    @unused supply: FreshNameSupply
  ): IR.Expression = {
    expression
  }

  // === Pass Metadata ========================================================

  /** States whether or not the binding was ignored. */
  sealed trait State extends IRPass.Metadata
  object IgnoreState {

    /** States that the binding is ignored. */
    case object Ignored extends State {
      override val metadataName: String = "IgnoredBindings.State.Ignored"
    }

    /** States that the binding is not ignored. */
    case object NotIgnored extends State {
      override val metadataName: String = "IgnoredBindings.State.NotIgnored"
    }
  }
}
