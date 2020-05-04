package org.enso.compiler.test.pass.desugar

import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.desugar.GenerateMethodBodies
import org.enso.compiler.test.CompilerTest

class SectionsToBinOpTest extends CompilerTest {

  // === Test Configuration ===================================================

  val passes: List[IRPass] = List(
    GenerateMethodBodies
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(passes, passConfiguration)

  // === The Tests ============================================================

  "Operator section desugaring" should {
    "work for left sections" in {
      pending
    }

    "work for sides sections" in {
      pending
    }

    "work for right sections" in {
      pending
    }

    "work when the section is nested" in {
      pending
    }
  }

}
