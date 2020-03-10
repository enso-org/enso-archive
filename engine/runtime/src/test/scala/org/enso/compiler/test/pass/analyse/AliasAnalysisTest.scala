package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.desugar.{LiftSpecialOperators, OperatorToFunction}
import org.enso.compiler.test.CompilerTest

class AliasAnalysisTest extends CompilerTest {

  // === Utilities ============================================================

  // TODO [AA] Wrap these functions as extensions
  implicit class Preprocess(source: String) {
    val precursorPasses: List[IRPass] = List(
      LiftSpecialOperators,
      OperatorToFunction
    )

    def preprocess: IR.Module = {
      val ir=toIRModule(source)
      runPasses(ir, precursorPasses).asInstanceOf[IR.Module]
    }
  }

  // === The Tests ============================================================

  "Alias analysis" should {
    val ir =
      """
        |main =
        |    a = 2 + 2
        |    b = a * a
        |    c = a -> a + b
        |
        |    IO.println 2.c
        |""".stripMargin.preprocess

    "do the thing" in {
      println(AliasAnalysis.runModule(ir).bindings.map(_.getMetadata[AliasAnalysis.Info].get))
    }

  }
}
