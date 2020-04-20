package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

// TODO [AA] Every name needs an internal ID, including dynamic symbols.
/** This pass implements dataflow analysis for Enso.
  *
  * Dataflow analysis is the processes of determining the dependencies between
  * program expressions.
  *
  * This pass needs to be run after [[AliasAnalysis]], [[DemandAnalysis]], and
  * [[TailCall]]. It also assumes that all members of [[IR.IRKind.Primitive]]
  * have been removed from the IR by the time it runs.
  */
case object DataflowAnalysis extends IRPass {
  override type Metadata = IR.Metadata.Empty

  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, annotated with data dependency information
    */
  override def runModule(ir: IR.Module): IR.Module = ir

  // TODO [AA] Work out how the expression flow can update the module and
  //  function metadata.
  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, annotated with data dependency information
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir

  // === Pass Internals =======================================================



  // === Pass Metadata ========================================================

  // TODO [AA] Some way of identifying things. Note, this pass doesn't attempt
  //  to deal with the fact that IDs change on code update.
  // TODO [AA] Need to produce global data.
  //  - Function Body, global symbol
  //  - Two types of metadata (one global attached to modules, and one local
  //    attached to functions)
  //  - Interdependent global symbols as a problem

}
