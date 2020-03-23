package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Module.Scope.Definition.Method
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{
  AliasAnalysis,
  ApplicationSaturation,
  TailCall
}
import org.enso.compiler.pass.desugar.{
  GenMethodBodies,
  LiftSpecialOperators,
  OperatorToFunction
}
import org.enso.compiler.test.CompilerTest
import org.enso.interpreter.runtime.scope.LocalScope

class TailCallTest extends CompilerTest {

  // === Test Setup ===========================================================

  val precursorPasses: List[IRPass] = List(
    GenMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis,
    ApplicationSaturation()
  )

  implicit class PreprocessModule(code: String) {
    def preprocessModule: IR.Module = {
      code.toIrModule
        .runPasses(precursorPasses, Some(LocalScope.root))
        .asInstanceOf[IR.Module]
    }
  }

  // === The Tests ============================================================

  "Tail call analysis" should {
    val ir =
      """
        |main =
        |    ifTest = c ~ifT ~ifF -> ifZero c ~ifT ~ifF
        |    sum = c acc -> ifTest c acc (sum c-1 acc+c)
        |    sum 10000 0
        |""".stripMargin.preprocessModule

    val resultIR       = TailCall.runModule(ir)
    val resultIRMethod = resultIR.bindings.head.asInstanceOf[Method]

    "be associated with every expression" in {
      val tmp = resultIRMethod.body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]
        .returnValue
        .getMetadata[TailCall.Metadata]
//        .expressions(0)
//        .asInstanceOf[IR.Expression.Binding]
//        .expression
//        .asInstanceOf[IR.Function.Lambda]
//        .body
//        .getMetadata[TailCall.Metadata]

//      println(tmp)
    }
  }
}
