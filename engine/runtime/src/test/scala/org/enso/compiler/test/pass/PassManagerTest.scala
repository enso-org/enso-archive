package org.enso.compiler.test.pass

import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{DataflowAnalysis, DemandAnalysis}
import org.enso.compiler.pass.optimise.LambdaConsolidate
import org.enso.compiler.test.CompilerTest

class PassManagerTest extends CompilerTest {

  // === Test Setup ===========================================================

  val inputPassOrdering: List[IRPass] = List(
    LambdaConsolidate,
    DemandAnalysis,
    DataflowAnalysis
  )

  val passConfiguration: List[IRPass.Configuration] = List()

  // === The Tests ============================================================

  "The pass manager" should {
    "raise an error due to invalidations" in {
      pending
    }

    "allow a valid pass ordering" in {
      pending
    }
  }
}
