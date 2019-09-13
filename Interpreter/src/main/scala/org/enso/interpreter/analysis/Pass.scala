package org.enso.interpreter.analysis

/**
  * A [[Pass]] is a representation of a transformation from one
  *
  * A pass contains:
  * - DependsOn: A representation of the passes a given pass depends on.
  * - evaluatePass: A function that executes the transformation represented by
  *   the pass.
  * - passName: The pass name.
  */
trait Pass[ASTIn, ASTOut] {}
