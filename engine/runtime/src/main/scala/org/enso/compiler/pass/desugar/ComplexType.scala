package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Module.Scope.Definition
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** Desugars complex type definitions to simple type definitions in the module
  * scope.
  *
  * Note that this pass currently ignores the creation of a function
  * representing the type (e.g. `maybe a = Nothing | Just a` as this does not
  * have a runtime representation at the current time.
  *
  * This pass has no configuration.
  *
  * This pass requires the context to provide:
  *
  * - Nothing
  *
  * It must have the following passes run before it:
  *
  * - None
  */
// TODO [AA] Need to disallow interface definitions for now as a syntax error.
// TODO [AA] Params should currently be a syntax error.
case object ComplexType extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Performs desugaring of complex type definitions for a module.
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
  ): IR.Module = ir.copy(
    bindings = ir.bindings.flatMap {
      case typ: Definition.Type => desugarComplexType(typ)
      case b                    => List(b)
    }
  )

  /** An identity operation on an arbitrary expression.
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

  /** Desugars a complex type definition into a series of top-level definitions.
    *
    * @param typ the type definition to desugar
    * @return the top-level definitions corresponding to the desugaring of `typ`
    */
  def desugarComplexType(
    @unused typ: IR.Module.Scope.Definition.Type
  ): List[IR.Module.Scope.Definition] = {
    List()
  }
}
