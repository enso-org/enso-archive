package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.desugar.{LiftSpecialOperators, OperatorToFunction}
import org.enso.compiler.test.CompilerTest

class AliasAnalysisTest extends CompilerTest {

  // === Utilities ============================================================

  /** Adds an extension method to preprocess the source as IR.
    *
    * @param source the source code to preprocess
    */
  implicit class Preprocess(source: String) {
    val precursorPasses: List[IRPass] = List(
      LiftSpecialOperators,
      OperatorToFunction
    )

    /** Translates the source code into appropriate IR for testing this pass.
      *
      * @return IR appropriate for testing the alias analysis pass
      */
    def preprocess: IR.Module = {
      source.toIRModule.runPasses(precursorPasses).asInstanceOf[IR.Module]
    }
  }

  // === The Tests ============================================================

  // TODO [AA] Some property-based testing using ScalaCheck

  "The alias scope" should {
    val flatScope = new Graph.Scope()
    val nestedScope = new Graph.Scope()

    "Have a number of scopes of 1 without children" in {
      flatScope.numScopes shouldEqual 1
    }

    "Have a nesting level of 1 without children" in {
      flatScope.nesting shouldEqual 1
    }
  }

  "The alias graph" should {}

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
//      println(
//        AliasAnalysis
//          .runModule(ir)
//          .bindings
//          .map(_.getMetadata[AliasAnalysis.Info].get)
//      )
    }

    val ir2 =
      """
        |type MyAtom a b=c (c = a + b)
        |""".stripMargin.preprocess

    "do the other thing" in {
//      println(
//        AliasAnalysis
//          .runModule(ir2)
//          .bindings
//          .map(_.getMetadata[AliasAnalysis.Info].get)
//      )
    }
  }
}
