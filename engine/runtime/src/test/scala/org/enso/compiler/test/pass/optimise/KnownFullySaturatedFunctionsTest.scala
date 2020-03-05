package org.enso.compiler.test.pass.optimise

import org.enso.compiler.pass.optimise.KnownFullySaturatedFunctions.{
  FunctionInfo,
  PassConfiguration
}
import org.enso.compiler.test.CompilerTest

class KnownFullySaturatedFunctionsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val knownFunctions: PassConfiguration = Map(
//    "+" -> FunctionInfo(2, ???),
//    "foo" -> FunctionInfo(3, ???)
  )

  // === The Tests ============================================================

  // TODO [AA] A pass that tests scoping properly
}
