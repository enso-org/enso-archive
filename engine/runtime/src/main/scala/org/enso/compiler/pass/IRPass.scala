package org.enso.compiler.pass

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR

/** A representation of a compiler pass that runs on the [[IR]] type. */
trait IRPass {

  /** The type of the metadata object that the pass writes to the IR. */
  type Metadata <: IR.Metadata

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir`.
    *
    * @param ir the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  def runModule(ir: IR.Module, moduleContext: ModuleContext): IR.Module

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir` in an inline context.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression
}
