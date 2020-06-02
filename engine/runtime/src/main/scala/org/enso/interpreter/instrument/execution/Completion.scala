package org.enso.interpreter.instrument.execution

sealed trait Completion

object Completion {

  /**
    * Signals completion of computations.
    */
  case object Done extends Completion

  case object Interrupted extends Completion

}
