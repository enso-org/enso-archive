package org.enso.compiler.test.pass

import org.enso.compiler.pass.IRPass
import org.enso.compiler.test.CompilerTest

class PassManagerTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passConfiguration: List[IRPass.Configuration] = List()

  // === The Tests ============================================================

  "The pass manager" should {
    "compute a valid pass ordering based on the pass precursors" in {
      pending
    }

    "respect fixed ordering" in {
      pending
    }

    "raise an error if a cycle is detected" in {
      pending
    }
  }
}
