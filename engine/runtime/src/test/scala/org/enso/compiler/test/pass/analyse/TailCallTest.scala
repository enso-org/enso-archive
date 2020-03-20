package org.enso.compiler.test.pass.analyse

import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{AliasAnalysis, ApplicationSaturation}
import org.enso.compiler.pass.desugar.{
  GenMethodBodies,
  LiftSpecialOperators,
  OperatorToFunction
}
import org.enso.compiler.test.CompilerTest

class TailCallTest extends CompilerTest {

  // === Test Setup ===========================================================

  val precursorPasses: List[IRPass] = List(
    GenMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis,
    ApplicationSaturation()
  )

  // === The Tests ============================================================

}
