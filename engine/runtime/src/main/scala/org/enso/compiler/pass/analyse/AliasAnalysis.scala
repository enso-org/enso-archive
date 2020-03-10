package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

case object AliasAnalysis extends IRPass {

  override type Metadata = IR.Metadata.Empty

  override def runModule(ir: IR.Module): IR.Module = ir

  override def runExpression(ir: IR.Expression): IR.Expression = ir

  // === Data Definitions =====================================================
  sealed trait Description {}
  object Description {
    sealed case class Scope() {}
    sealed case class Definition() {}
    sealed case class Usage () {}
  }
}
