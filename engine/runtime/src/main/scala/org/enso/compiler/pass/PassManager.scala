package org.enso.compiler.pass

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR

// TODO [AA] Needs to set the 'writeToContext' flag in pass configuration for
//  the last exec of each pass
/** The pass manager is responsible for executing the provided passes in order.
  *
  * @param passOrdering the specification of the ordering for the passes
  * @param passConfiguration the configuration for the passes
  */
class PassManager(
  passOrdering: List[IRPass],
  passConfiguration: PassConfiguration
) {

  // TODO [AA] Refactor common from this
  /** Executes the passes on an [[IR.Module]].
    *
    * @param ir the module to execute the compiler phases on
    * @param moduleContext the module context in which the phases are executed
    * @return the result of executing [[passOrdering]] on `ir`
    */
  def runPassesOnModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = {
    val newContext =
      moduleContext.copy(passConfiguration = Some(passConfiguration))

    passOrdering.foldLeft(ir)((intermediateIR, pass) =>
      pass.runModule(intermediateIR, newContext)
    )
  }

  /** Executes the passes on an [[IR.Expression]].
    *
    * @param ir the expression to execute the compiler phases on
    * @param inlineContext the inline context in which the expression is
    *                      processed
    * @return the result of executing [[passOrdering]] on `ir`
    */
  def runPassesInline(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val newContext =
      inlineContext.copy(passConfiguration = Some(passConfiguration))

    passOrdering.foldLeft(ir)((intermediateIR, pass) =>
      pass.runExpression(intermediateIR, newContext)
    )
  }
}
