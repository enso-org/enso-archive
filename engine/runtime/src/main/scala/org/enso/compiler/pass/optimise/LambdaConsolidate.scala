package org.enso.compiler.pass.optimise

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

// TODO [AA] Add the concept of a `Warning` to the codebase
// TODO [AA] Introduce a warning if a lambda chain shadows a var
// TODO [AA] Account for defaults
// TODO [AA] Re-make it possible to run alias analysis multiple times
/** This pass consolidates chains of lambdas into multi-argument lambdas
  * internally.
  *
  * Enso's syntax, due to its unified design, only supports single-argument
  * lambda expressions. However, internally, we want to be able to use
  * multi-argument lambda expressions for performance reasons. This pass turns
  * these chains of lambda expressions into multi-argument lambdas.
  */
case object LambdaConsolidate extends IRPass {
  override type Metadata = IR.Metadata.Empty

  override def runModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = ir

  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir
}
