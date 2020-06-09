package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{AliasAnalysis, CachePreferenceAnalysis, DataflowAnalysis, DemandAnalysis, TailCall}
import org.enso.compiler.pass.lint.UnusedBindings

import scala.annotation.unused

/** This pass is responsible for resolving type signatures and associating
  * them as metadata with the typed object.
  *
  * This pass requires the context to provide:
  *
  * - Nothing
  */
case object TypeSignatures extends IRPass {
  override type Metadata = Signature
  override type Config   = IRPass.Configuration.Default

  override val precursorPasses: Seq[IRPass] = List(
    TypeFunctions
  )
  override val invalidatedPasses: Seq[IRPass] = List(
    AliasAnalysis,
    CachePreferenceAnalysis,
    DataflowAnalysis,
    DemandAnalysis,
    TailCall,
    UnusedBindings
  )

  /** Resolves type signatures in a module.
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

  /** Resolves type signatures in an expression.
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

  // === Pass Internals =======================================================

  // === Metadata =============================================================

  /** A representation of a type signature.
   *
   * @param signature the expression for the type signature
   */
  case class Signature(signature: IR.Expression) extends IRPass.Metadata {
    override val metadataName: String = "TypeSignatures.Signature"
  }
}
