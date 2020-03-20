package org.enso.compiler.pass.desugar

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}

// TODO [AA] Should only insert `this` if it doesn't already exist in the args
//  list.
/** This pass is responsible for ensuring that method bodies are in the correct
  * format.
  *
  * The correct format as far as the rest of the compiler pipeline is concerned
  * is as follows:
  *
  * - The body is a function (lambda)
  * - The body has `this` in its argument list
  */
case object GenMethodBodies extends IRPass {

  /** This is a desugaring pass and performs no analysis */
  override type Metadata = IR.Metadata.Empty

  /** Generates and consolidates method bodies.
    *
    * @param ir the Enso IR to process
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(ir: IR.Module): IR.Module = {
    ir.copy(
      bindings = ir.bindings.map {
        case m: IR.Module.Scope.Definition.Method => processMethodDef(m)
        case x                                    => x
      }
    )
  }

  /** Processes a method definition, ensuring that it's in the correct format.
    *
    * @param ir the method definition to process
    * @return `ir` potentially with alterations to ensure that it's in the
    *         correct format
    */
  def processMethodDef(
    ir: IR.Module.Scope.Definition.Method
  ): IR.Module.Scope.Definition.Method = {
    ir
  }

  /** Executes the pass on an expression.
    *
    * It is a identity operation on expressions as method definitions are not
    * expressions.
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
}
