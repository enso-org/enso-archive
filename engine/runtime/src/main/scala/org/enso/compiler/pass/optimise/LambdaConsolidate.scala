package org.enso.compiler.pass.optimise

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

// TODO [AA] Need to fix the hack in AliasAnalysis about this.
// TODO [AA] Need a fresh name supply
// TODO [AA] Add the concept of a `Warning` to the codebase
case object LambdaConsolidate extends IRPass {
  override type Metadata = IR.Metadata.Empty

  override def runModule(ir: IR.Module): IR.Module = ir

  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir
}
