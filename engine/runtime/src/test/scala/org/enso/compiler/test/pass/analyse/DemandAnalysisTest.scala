package org.enso.compiler.test.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{AliasAnalysis, DemandAnalysis}
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  LiftSpecialOperators,
  OperatorToFunction
}
import org.enso.compiler.test.CompilerTest
import org.enso.interpreter.runtime.scope.LocalScope

class DemandAnalysisTest extends CompilerTest {

  // === Test Setup ===========================================================

  /** The passes that must be run before the demand analysis pass. */
  implicit val precursorPasses: List[IRPass] = List(
    GenerateMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis
  )

  /** Adds an extension method to run alias analysis on an [[IR.Module]].
    *
    * @param ir the module to run alias analysis on
    */
  implicit class AnalyseModule(ir: IR.Module) {

    /** Runs demand analysis on a module.
      *
      * @return [[ir]], transformed by the demand analysis pass
      */
    def analyse: IR.Module = {
      DemandAnalysis.runModule(ir)
    }
  }

  /** Adds an extension method to run alias analysis on an [[IR.Expression]].
    *
    * @param ir the expression to run alias analysis on
    */
  implicit class AnalyseExpression(ir: IR.Expression) {

    /** Runs demand analysis on an expression.
      *
      * @param inlineContext the inline context in which to process the
      *                      expression
      * @return [[ir]], transformed by the demand analysis pass
      */
    def analyse(implicit inlineContext: InlineContext): IR.Expression = {
      DemandAnalysis.runExpression(ir, inlineContext)
    }
  }

  // === The Tests ============================================================

  // TODO [AA] The only cases where force needs to be inserted are `a` and
  //  `b = a` (`a -> b -> a`)
  "Suspended arguments" should {
    "do the thing" in {
      implicit val ctx: InlineContext =
        InlineContext(localScope = Some(LocalScope.root))

      val ir =
        """~x ~y z ->
          |  a = x
          |  x + y + z
          |""".stripMargin.preprocessExpression.get.analyse
    }

//    "not be forced when passed to functions" in {
//      implicit val ctx: InlineContext =
//        InlineContext(localScope = Some(LocalScope.root))
//
//      val code =
//        """~x ~y z -> foo x y z
//          |""".stripMargin.preprocessExpression.get.analyse
//
//      pending
//    }
  }

  "Non-suspended arguments" should {
//    "be left alone by demand analysis" in {
//      implicit val ctx: InlineContext =
//        InlineContext(localScope = Some(LocalScope.root))
//
//      val code =
//        """x y ->
//          |  a = x + u
//          |  foo x a
//          |""".stripMargin.preprocessExpression.get.analyse
//
//      pending
//    }
  }

  "Demand analysis" should {
//    "work properly on modules" in {
//      implicit val ctx: InlineContext =
//        InlineContext(localScope = Some(LocalScope.root))
//
//      val code =
//        """T.foo = ~x y -> this.bar x + y
//          |
//          |T.bar = ~x -> IO.println x
//          |""".stripMargin.preprocessExpression.get.analyse
//
//      pending
//    }
//
//    "work properly on expressions" in {
//      implicit val ctx: InlineContext =
//        InlineContext(localScope = Some(LocalScope.root))
//
//      val code =
//        """~a ~b c d -> a + b + c + d
//          |""".stripMargin.preprocessExpression.get.analyse
//
//      pending
//    }
  }
}
