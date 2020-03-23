package org.enso.compiler.test.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Module.Scope.Definition.Method
import org.enso.compiler.exception.CompilerError
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

  val ctx = InlineContext(Some(LocalScope.root))

  val precursorPasses: List[IRPass] = List(
    GenMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis,
    ApplicationSaturation()
  )

  /** Adds an extension method to preprocess source code as an Enso module.
    *
    * @param code the source code to preprocess
    */
  implicit class PreprocessModule(code: String) {

    /** Preprocesses the provided source code into an [[IR.Module]].
      *
      * @return the IR representation of [[code]]
      */
    def preprocessModule: IR.Module = {
      code.toIrModule
        .runPasses(precursorPasses, ctx)
        .asInstanceOf[IR.Module]
    }
  }

  /** Adds an extension method to preprocess source code as an Enso expression.
   *
   * @param code the source code to preprocess
   */
  implicit class PreprocessExpression(code: String) {

    /** Preprocesses the provided source code into an [[IR.Expression]].
     *
     * @return the IR representation of [[code]]
     */
    def preprocessExpression: IR.Expression = {
      code.toIrExpression
        .getOrElse(
          throw new CompilerError("Code was not a valid expression.")
        )
        .runPasses(precursorPasses, ctx)
        .asInstanceOf[IR.Expression]
    }
  }

  // === The Tests ============================================================

  "Tail call analysis on modules" should {}

  "Tail call analysis on expressions" should {}

  "Tail call analysis on functions" should {}

  "Tail call analysis on case expressions" should {}

  "Tail call analysis on function call arguments" should {}

  "Tail call analysis on blocks" should {

    "mark the bodies of bound functions as tail properly" in {
      pending
    }
  }

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
    val resultIRBlock = resultIRMethod.body
      .asInstanceOf[IR.Function.Lambda]
      .body
      .asInstanceOf[IR.Expression.Block]

    "be associated with every expression" in {
      val ifTestBodyIsTail = resultIRBlock.expressions.head
        .asInstanceOf[IR.Expression.Binding]
        .expression
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Application.Prefix]
        .getMetadata[TailCall.Metadata]
        .get

      val sumBodyIsTail = resultIRBlock
        .expressions(1)
        .asInstanceOf[IR.Expression.Binding]
        .expression
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Application.Prefix]
        .getMetadata[TailCall.Metadata]
        .get

    }
  }
}
