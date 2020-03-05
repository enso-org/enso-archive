package org.enso.compiler.pass.optimise

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.optimise.KnownFullySaturatedFunctions.{
  FunctionInfo,
  PassConfiguration
}
import org.enso.interpreter.node.{ExpressionNode => RuntimeExpression}

/** This optimisation pass recognises fully-saturated applications of known
  * functions and writes analysis data that optimises them to specific nodes at
  * codegen time.
  *
  * PLEASE NOTE: This implementation is _incomplete_ as the rewrite it performs
  * is _unconditional_ at this stage. This means that, until we have alias
  * analysis information,
  *
  * @param knownFunctions a mapping from known function names to information
  *                       about that function that can be used for optimisation
  */
case class KnownFullySaturatedFunctions(
  knownFunctions: PassConfiguration
) extends IRPass {

  // TODO [AA]
  override type Metadata = FunctionInfo

  override def runModule(ir: IR.Module): IR.Module = ir

  override def runExpression(ir: IR.Expression): IR.Expression = ir
}
object KnownFullySaturatedFunctions {
  type CodegenHelper     = IR.Application => RuntimeExpression
  type PassConfiguration = Map[String, FunctionInfo]

  case class FunctionInfo(
    arity: Int,
    codegenHelper: CodegenHelper
  ) extends IR.Metadata

  object Default {
    val Config: PassConfiguration = Map()
  }
}
