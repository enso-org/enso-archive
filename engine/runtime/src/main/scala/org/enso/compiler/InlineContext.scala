package org.enso.compiler

import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}

/** A type containing the information about the execution context for an inline
  * expression.
  *
  * @param localScope the local scope in which the expression is being executed
  * @param moduleScope the module scope in which the expression is being
  *                    executed
  * @param isInTailPosition whether or not the inline expression occurs in tail
  *                         position ([[None]] indicates no information)
  */
case class InlineContext(
  localScope: Option[LocalScope]    = None,
  moduleScope: Option[ModuleScope]  = None,
  isInTailPosition: Option[Boolean] = None
)
