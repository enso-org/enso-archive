package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Application
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{
  AliasAnalysis,
  CachePreferenceAnalysis,
  DataflowAnalysis,
  DemandAnalysis,
  TailCall
}
import org.enso.compiler.pass.desugar.{
  LambdaShorthandToLambda,
  NestedPatternMatch,
  OperatorToFunction,
  SectionsToBinOp
}
import org.enso.compiler.pass.lint.UnusedBindings

import scala.annotation.unused

/** This pass is responsible for lifting applications of type functions such as
  * `:` and `in` and `!` into their specific IR nodes.
  *
  * This pass requires the context to provide:
  *
  * - Nothing
  */
case object TypeFunctions extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  override val precursorPasses: Seq[IRPass] = List(
    IgnoredBindings,
    LambdaShorthandToLambda,
    OperatorToFunction,
    SectionsToBinOp
  )

  override val invalidatedPasses: Seq[IRPass] = List(
    AliasAnalysis,
    CachePreferenceAnalysis,
    DataflowAnalysis,
    DemandAnalysis,
    TailCall,
    UnusedBindings
  )

  /** Performs typing function resolution on a module.
    *
    * @param ir the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(
    ir: IR.Module,
    @unused moduleContext: ModuleContext
  ): IR.Module = ir.transformExpressions {
    case a => resolveExpression(a)
  }

  /** Performs typing function resolution on an expression.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runExpression(
    ir: IR.Expression,
    @unused inlineContext: InlineContext
  ): IR.Expression = ir.transformExpressions {
    case a => resolveExpression(a)
  }

  // === Pass Internals =======================================================

  /** Performs resolution of typing functions in an arbitrary expression.
    *
    * @param expr the expression to perform resolution in
    * @return `expr`, with any typing functions resolved
    */
  def resolveExpression(expr: IR.Expression): IR.Expression = {
    expr.transformExpressions {
      case app: IR.Application => resolveApplication(app)
    }
  }

  /** Performs resolution of typing functions in an application.
   *
   * @param app the application to perform resolution in
   * @return `app`, with any typing functions resolved
   */
  def resolveApplication(app: IR.Application): IR.Application = {
    app match {
      case Application.Prefix(_, _, _, _, _, _)     => app
      case Application.Force(_, _, _, _)            => app
      case Application.Literal.Sequence(_, _, _, _) => app
      case Application.Literal.Typeset(_, _, _, _)  => app
      case _: Application.Operator =>
        throw new CompilerError(
          "Operators should not be present during typing functions lifting."
        )
    }
  }

  // === Utilities ============================================================

  /** Checks if a call argument is valid for a typing expression.
    *
    * As all typing functions are _operators_ in the source, their arguments
    * must:
    *
    * - Not have a name defined.
    * - Have no suspension info or not be suspended
    *
    * @param arg the argument to check
    * @return `true` if `arg` is valid, otherwise `false`
    */
  def isValidCallArg(arg: IR.CallArgument): Boolean = {
    arg match {
      case IR.CallArgument.Specified(name, _, _, susp, _, _) =>
        name.isEmpty && (susp.isEmpty || susp.get)
    }
  }
}
