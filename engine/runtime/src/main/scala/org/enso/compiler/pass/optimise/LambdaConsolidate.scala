package org.enso.compiler.pass.optimise

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

// TODO [AA] Need to be able to run alias analysis _again_ after this.
// TODO [AA] Add primitives to the IR for overwriting pass metadata (need to
//  make it a HSet?)
// TODO [AA] Need to fix the hack in AliasAnalysis about this.
// TODO [AA] Need a fresh name supply
case object LambdaConsolidate extends IRPass {
  override type Metadata = IR.Metadata.Empty

  override def runModule(ir: IR.Module): IR.Module = ir

  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir
}
