package org.enso.compiler.pass

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR

/** A representation of a compiler pass that runs on the [[IR]] type.
  *
  * Passes that depend on the metadata of other passes should pull this metadata
  * directly from the IR, and not depend on metadata available in the context.
  */
trait IRPass {

  /** The type of the metadata object that the pass writes to the IR. */
  type Metadata <: IR.Metadata

  /** The type of configuration for the pass. */
  type Config <: IRPass.Configuration

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
object IRPass {

  /** A representation of configuration for a given pass. */
  trait Configuration {

    /** Whether or not the pass should write to the context. */
    val shouldWriteToContext: Boolean
  }
  object Configuration {
    case class Default() extends Configuration {
      override val shouldWriteToContext: Boolean = false
    }
  }
}
