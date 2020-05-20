package org.enso.compiler.pass.desugar

/** Desugars complex type definitions to simple type definitions in the module
  * scope.
  */
// TODO [AA] Need to disallow interface definitions for now.
case object ComplexType
