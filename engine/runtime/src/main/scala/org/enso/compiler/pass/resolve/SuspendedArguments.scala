package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{
  AliasAnalysis,
  CachePreferenceAnalysis,
  DataflowAnalysis,
  DemandAnalysis,
  TailCall
}
import org.enso.compiler.pass.lint.UnusedBindings
import org.enso.compiler.pass.optimise.LambdaConsolidate

import scala.annotation.unused

/** This pass is responsible for analysing type signatures to determine which
  * arguments in a function definition are suspended.
  *
  * It searches for a correspondence between an argument position and the same
  * position in the associated type signature, marking the argument as suspended
  * if its type contains the _top-level_ constructor `Suspended`.
  *
  * This pass requires the context to provide:
  *
  * - Nothing
  */
case object SuspendedArguments extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  override val precursorPasses: Seq[IRPass] = List(TypeSignatures)
  override val invalidatedPasses: Seq[IRPass] = List(
    AliasAnalysis,
    CachePreferenceAnalysis,
    DataflowAnalysis,
    DataflowAnalysis,
    DemandAnalysis,
    LambdaConsolidate,
    TailCall,
    UnusedBindings
  )

  /** Resolves suspended arguments in a module.
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
  ): IR.Module = ir

  /** Resolves suspended arguments in an arbitrary expression.
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
  ): IR.Expression = ir
}
