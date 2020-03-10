package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.desugar.{LiftSpecialOperators, OperatorToFunction}
import org.enso.compiler.test.CompilerTest

class AliasAnalysisTest extends CompilerTest {

  // === Utilities ============================================================

  val precursorPasses: List[IRPass] = List(
    LiftSpecialOperators,
    OperatorToFunction
  )

  // TODO [AA] Wrap these functions as extensions
  def preprocess(source: String): IR.Module = {
    val ir = toIRModule(source)
    runPasses(ir, precursorPasses).asInstanceOf[IR.Module]
  }

  // === The Tests ============================================================

  "Alias analysis" should {
    val source =
      """
        |main =
        |    a = 2 + 2
        |    b = a * a
        |    c = a -> a + b
        |
        |    IO.println 2.c
        |""".stripMargin

    val ir = preprocess(source)

    "do the thing" in {
      AliasAnalysis.runModule(ir)
    }

  }
}
