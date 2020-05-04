package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

/** This pass translates `_` arguments at application sites to lambda functions.
  *
  * It requires [[GenerateMethodBodies]], [[SectionsToBinOp]] and
  * [[OperatorToFunction]] to have run before it.
  */
case object UnderscoreToLambda extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Desugars underscore arguments to lambdas for a module.
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
  ): IR.Module = ir

  /** Desugars underscore arguments to lambdas for an arbitrary expression.
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
  ): IR.Expression = ir
}
