package org.enso.compiler.pass.lint

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** This pass performs linting for unused names, generating warnings if it finds
  * any.
  *
  * It requires [[org.enso.compiler.pass.resolve.IgnoredBindings]] and
  * [[org.enso.compiler.pass.analyse.AliasAnalysis]] to run before it.
  */
case object UnusedBindings extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Lints a module.
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
    case x => x.mapExpressions(runExpression(_, InlineContext()))
  }

  /** Lints an arbitrary expression.
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
  ): IR.Expression = ir.transformExpressions {
    case binding: IR.Expression.Binding => lintBinding(binding, inlineContext)
    case function: IR.Function          => lintFunction(function, inlineContext)
  }

  // === Pass Internals =======================================================

  /** Lints a binding.
    *
    * @param binding the binding to lint
    * @param context the inline context in which linting is taking place
    * @return `binding`, with any lints attached
    */
  def lintBinding(
    binding: IR.Expression.Binding,
    @unused context: InlineContext
  ): IR.Expression.Binding = {
    binding
  }

  /** Lints a function.
   *
   * @param function the function to lint
   * @param context the inline context in which linting is taking place
   * @return `function`, with any lints attached
   */
  def lintFunction(
    function: IR.Function,
    @unused context: InlineContext
  ): IR.Function = {
    function
  }
}
