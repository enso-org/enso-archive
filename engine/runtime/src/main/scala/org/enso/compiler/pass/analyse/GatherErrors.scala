package org.enso.compiler.pass.analyse

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

/**
  * A pass that traverses the given root IR and accumulates all the encountered
  * error nodes in the root.
  */
case object GatherErrors extends IRPass {

  override type Metadata = Errors

  /** Executes the pass on the provided `ir`, and attaches all the encountered
    * errors to its metadata storage.
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
  ): IR.Module =
    ir.addMetadata[Metadata, Metadata](gatherErrors(ir))

  /** Executes the pass on the provided `ir`, and attaches all the encountered
    * errors to its metadata storage.
    *
    * @param ir the IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir` with all the errors accumulated in pass metadata.
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir.addMetadata[Metadata, Metadata](gatherErrors(ir))

  private def gatherErrors(ir: IR): Errors =
    Errors(ir.preorder.collect { case err: IR.Error => err })

  /** A container for errors found in the IR.
   *
   * @param errors a list of the errors found in the IR
   */
  case class Errors(errors: List[IR.Error]) extends IR.Metadata {

    /** The name of the metadata as a string. */
    override val metadataName: String = "GatherErrors.Errors"
  }
}
