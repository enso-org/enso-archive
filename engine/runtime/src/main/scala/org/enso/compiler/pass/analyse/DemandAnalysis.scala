package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

/** This pass implements demand analysis for Enso.
  *
  * Demand analysis is the process of determining _when_ a suspended term needs
  * to be forced (where the suspended value is _demanded_). It does the
  * following:
  *
  * - Determines the usage sites where a suspended term needs to be forced.
  * - Inserts state annotations onto all usages to say whether they should be
  *   forced or not.
  *
  * This pass needs to be run after [[AliasAnalysis]].
  */
case object DemandAnalysis extends IRPass {
  override type Metadata = State

  /** Executes the demand analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, annotated with demand information
    */
  override def runModule(ir: IR.Module): IR.Module = ir

  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir

  // === Metadata =============================================================

  /** This trait represents the suspension state of a variable usage. */
  sealed trait State extends IR.Metadata {

    /** Whether or not the state represents the generation of a runtime force.
      */
    val generateForce: Boolean
  }
  object State {

    /** The variable usage annotated with this state needs to be forced. */
    final case object Demanded extends State {
      override val generateForce: Boolean = true
    }

    /** The variable usage annotated with this state should be left suspended.
      */
    final case object Suspended extends State {
      override val generateForce: Boolean = false
    }

    // === Conversions ========================================================

    /** An implicit conversion from [[State]] to [[Boolean]].
      *
      * @param st the state to convert to a boolean
      * @return the boolean representation of `st`
      */
    implicit def toBoolean(st: State): Boolean = st.generateForce

    /** Converts from a [[Boolean]] to a [[State]].
      *
      * @param shouldBeForced the boolean value to represent as a [[State]]
      * @return `boolean` represented as a [[State]]
      */
    def fromBoolean(shouldBeForced: Boolean): State =
      if (shouldBeForced) Demanded else Suspended
  }
}
