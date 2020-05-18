package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** This pass handles the desugaring of long-form function and method
 * definitions into standard bindings using lambdas.
 *
 * This works for any definition of the form `f <args> = <body>`.
 *
 * This pass has no configuration.
 *
 * This pass requires the context to provide:
 *
 * - Nothing
 *
 * It must have the following passes run before it:
 *
 * - [[org.enso.compiler.pass.desugar.GenerateMethodBodies]]
 */
case object FunctionBinding extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  override def runModule(
    ir: IR.Module,
    @unused moduleContext: ModuleContext
  ): IR.Module = ir

  override def runExpression(
    ir: IR.Expression,
    @unused inlineContext: InlineContext
  ): IR.Expression = ir
}
