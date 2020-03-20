package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}

// TODO [AA] Remove `canBeTCO` from IR.Function as it is no longer needed
case object TailCall extends IRPass {

  /** The annotation metadata type associated with IR nodes by this pass. */
  override type Metadata = TailState

  /** Analyses tail call state for expressions in a module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(ir: IR.Module): IR.Module = ir

  /** Analyses tail call state for an arbitrary expression.
    *
    * @param ir the Enso IR to process
    * @param localScope the local scope in which the expression is executed
    * @param moduleScope the module scope in which the expression is executed
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runExpression(
    ir: IR.Expression,
    localScope: Option[LocalScope],
    moduleScope: Option[ModuleScope]
  ): IR.Expression = ir

  /** Expresses the tail call state of an IR Node. */
  sealed trait TailState extends IR.Metadata {

    /** A boolean representation of the expression's tail state. */
    def bool: Boolean
  }
  object TailState {

    /** The expression is in a tail position and can be tail call optimised. */
    final case object Tail extends TailState {
      override def bool: Boolean = true
    }

    /** The expression is not in a tail position and cannot be tail call
      * optimised.
      */
    final case object NotTail extends TailState {
      override def bool: Boolean = false
    }
  }
}
