package org.enso.compiler.test.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.DataflowAnalysis.DependencyInfo
import org.enso.compiler.pass.analyse.{
  AliasAnalysis,
  DataflowAnalysis,
  DemandAnalysis,
  TailCall
}
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  LiftSpecialOperators,
  OperatorToFunction
}
import org.enso.compiler.test.CompilerTest

class DataflowAnalysisTest extends CompilerTest {

  // === Test Setup ===========================================================

  /** The passes that must be run before the dataflow analysis pass. */
  implicit val precursorPasses: List[IRPass] = List(
    GenerateMethodBodies,
    LiftSpecialOperators,
    OperatorToFunction,
    AliasAnalysis,
    DemandAnalysis,
    TailCall
  )

  /** Generates an identifier dependency.
    *
    * @return a randomly generated identifier dependency
    */
  def genStaticDep: DependencyInfo.Type = {
    DependencyInfo.Type.Static(genID)
  }

  /** Makes a statically known dependency from the included id.
    *
    * @param id the identifier to use as the id
    * @return a static dependency on the node given by `id`
    */
  def mkStaticDep(id: DependencyInfo.Identifier): DependencyInfo.Type = {
    DependencyInfo.Type.Static(id)
  }

  /** Makes a symbol dependency from the included string.
    *
    * @param str the string to use as a name
    * @return a symbol dependency on the symbol given by `str`
    */
  def mkDynamicDep(str: String): DependencyInfo.Type = {
    DependencyInfo.Type.Dynamic(str)
  }

  /** Adds an extension method to run dataflow analysis on an [[IR.Module]].
    *
    * @param ir the module to run dataflow analysis on.
    */
  implicit class AnalyseModule(ir: IR.Module) {

    /** Runs dataflow analysis on a module.
      *
      * @return [[ir]], with attached data dependency information
      */
    def analyse: IR.Module = {
      DataflowAnalysis.runModule(ir)
    }
  }

  /** Adds an extension method to run dataflow analysis on an [[IR.Expression]].
    *
    * @param ir the expression to run dataflow analysis on
    */
  implicit class AnalyseExpresion(ir: IR.Expression) {

    /** Runs dataflow analysis on an expression.
      *
      * @param inlineContext the inline context in which to process the
      *                      expression
      * @return [[ir]], with attached data dependency information
      */
    def analyse(implicit inlineContext: InlineContext): IR.Expression = {
      DataflowAnalysis.runExpression(ir, inlineContext)
    }
  }

  // === The Tests ============================================================

  "Dataflow metadata" should {
    "allow querying for expressions that should be invalidated on change" in {
      val dependencies = new DependencyInfo
      val ids          = List.fill(5)(genStaticDep)

      dependencies(ids.head) = Set(ids(1), ids(2))
      dependencies(ids(2))   = Set(ids(3), ids(4))
      dependencies(ids(4))   = Set(ids(1), ids.head)

      dependencies(ids.head) shouldEqual Set(
        ids(1),
        ids(2),
        ids(3),
        ids(4),
        ids.head
      )
    }

    "provide a safe query function as well" in {
      val dependencies = new DependencyInfo
      val ids          = List.fill(5)(genStaticDep)
      val badId        = genStaticDep

      dependencies(ids.head) = Set(ids(1), ids(2))
      dependencies(ids(2))   = Set(ids(3), ids(4))
      dependencies(ids(4))   = Set(ids(1), ids.head)

      dependencies.get(ids.head) shouldBe defined
      dependencies.get(badId) should not be defined

      dependencies.get(ids.head) shouldEqual Some(
        Set(
          ids(1),
          ids(2),
          ids(3),
          ids(4),
          ids.head
        )
      )
    }

    "allow querying only the direct dependents of a node" in {
      val dependencies = new DependencyInfo
      val ids          = List.fill(5)(genStaticDep)

      dependencies(ids.head) = Set(ids(1), ids(2))
      dependencies(ids(2))   = Set(ids(3), ids(4))
      dependencies(ids(4))   = Set(ids(1), ids.head)

      dependencies.getDirect(ids.head) shouldEqual Some(Set(ids(1), ids(2)))
      dependencies.getDirect(ids(2)) shouldEqual Some(Set(ids(3), ids(4)))
      dependencies.getDirect(ids(4)) shouldEqual Some(Set(ids(1), ids.head))
    }

    "allow for updating the dependents of a node" in {
      val dependencies = new DependencyInfo
      val ids          = List.fill(3)(genStaticDep)

      dependencies(ids.head) = Set(ids(1))
      dependencies(ids.head) shouldEqual Set(ids(1))

      dependencies(ids.head) = Set(ids(2))
      dependencies(ids.head) shouldEqual Set(ids(2))

      dependencies(ids.head) ++= Set(ids(1))
      dependencies(ids.head) shouldEqual Set(ids(1), ids(2))
    }

    "allow for updating at a given node" in {
      val dependencies = new DependencyInfo
      val ids          = List.fill(6)(genStaticDep)
      val set1         = Set.from(ids.tail)
      val newId        = genStaticDep

      dependencies.updateAt(ids.head, set1)
      dependencies(ids.head) shouldEqual set1

      dependencies.updateAt(ids.head, Set(newId))
      dependencies(ids.head) shouldEqual (set1 + newId)
    }

    "allow combining the information from multiple modules" in {
      val module1 = new DependencyInfo
      val module2 = new DependencyInfo

      val symbol1 = mkDynamicDep("foo")
      val symbol2 = mkDynamicDep("bar")
      val symbol3 = mkDynamicDep("baz")

      val symbol1DependentIdsInModule1 = Set(genStaticDep, genStaticDep)
      val symbol2DependentIdsInModule1 = Set(genStaticDep, genStaticDep)
      val symbol1DependentIdsInModule2 = Set(genStaticDep, genStaticDep)
      val symbol3DependentIdsInModule2 = Set(genStaticDep)

      module1(symbol1) = symbol1DependentIdsInModule1
      module1(symbol2) = symbol2DependentIdsInModule1
      module2(symbol1) = symbol1DependentIdsInModule2
      module2(symbol3) = symbol3DependentIdsInModule2

      val combinedModule = module1 ++ module2

      combinedModule.get(symbol1) shouldBe defined
      combinedModule.get(symbol2) shouldBe defined
      combinedModule.get(symbol3) shouldBe defined

      combinedModule(symbol1) shouldEqual (symbol1DependentIdsInModule1 ++ symbol1DependentIdsInModule2)
      combinedModule(symbol2) shouldEqual symbol2DependentIdsInModule1
      combinedModule(symbol3) shouldEqual symbol3DependentIdsInModule2
    }
  }

  "Dataflow analysis on modules" should {
    val ir =
      """
        |M.foo = a b ->
        |   IO.println b
        |   c = a + b
        |   frobnicate a c
        |""".stripMargin.preprocessModule.analyse

    val depInfo = ir.getMetadata[DataflowAnalysis.DependencyInfo].get

    // The method and body
    val method =
      ir.bindings.head.asInstanceOf[IR.Module.Scope.Definition.Method]
    val fn     = method.body.asInstanceOf[IR.Function.Lambda]
    val fnArgA = fn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified]
    val fnArgB = fn.arguments(1).asInstanceOf[IR.DefinitionArgument.Specified]
    val fnBody = fn.body.asInstanceOf[IR.Expression.Block]

    // The `IO.println` expression
    val printlnExpr =
      fnBody.expressions.head.asInstanceOf[IR.Application.Prefix]
    val printlnArgIO =
      printlnExpr.arguments.head.asInstanceOf[IR.CallArgument.Specified]
    val printlnArgIOExpr = printlnArgIO.value.asInstanceOf[IR.Name.Literal]
    val printlnArgB =
      printlnExpr.arguments(1).asInstanceOf[IR.CallArgument.Specified]
    val printlnArgBExpr = printlnArgB.value.asInstanceOf[IR.Name.Literal]

    // The `c =` expression
    val cBindExpr  = fnBody.expressions(1).asInstanceOf[IR.Expression.Binding]
    val cBindName  = cBindExpr.name.asInstanceOf[IR.Name.Literal]
    val plusExpr   = cBindExpr.expression.asInstanceOf[IR.Application.Prefix]
    val plusExprFn = plusExpr.function.asInstanceOf[IR.Name.Literal]
    val plusExprArgA =
      plusExpr.arguments.head.asInstanceOf[IR.CallArgument.Specified]
    val plusExprArgAExpr = plusExprArgA.value.asInstanceOf[IR.Name.Literal]
    val plusExprArgB =
      plusExpr.arguments(1).asInstanceOf[IR.CallArgument.Specified]
    val plusExprArgBExpr = plusExprArgB.value.asInstanceOf[IR.Name.Literal]

    // The `frobnicate` return expression
    val frobExpr = fnBody.returnValue.asInstanceOf[IR.Application.Prefix]
    val frobFn   = frobExpr.function.asInstanceOf[IR.Name.Literal]
    val frobArgA =
      frobExpr.arguments.head.asInstanceOf[IR.CallArgument.Specified]
    val frobArgAExpr = frobArgA.value.asInstanceOf[IR.Name.Literal]
    val frobArgC     = frobExpr.arguments(1).asInstanceOf[IR.CallArgument.Specified]
    val frobArgCExpr = frobArgC.value.asInstanceOf[IR.Name.Literal]

    // The global symbols
    val frobnicateSymbol = mkDynamicDep("frobnicate")
    val ioSymbol         = mkDynamicDep("IO")
    val printlnSymbol    = mkDynamicDep("println")
    val plusSymbol       = mkDynamicDep("+")

    // The Identifiers
    val methodId           = mkStaticDep(method.getId)
    val fnId               = mkStaticDep(fn.getId)
    val fnArgAId           = mkStaticDep(fnArgA.getId)
    val fnArgBId           = mkStaticDep(fnArgB.getId)
    val fnBodyId           = mkStaticDep(fnBody.getId)
    val printlnExprId      = mkStaticDep(printlnExpr.getId)
    val printlnArgIOId     = mkStaticDep(printlnArgIO.getId)
    val printlnArgIOExprId = mkStaticDep(printlnArgIOExpr.getId)
    val printlnArgBId      = mkStaticDep(printlnArgB.getId)
    val printlnArgBExprId  = mkStaticDep(printlnArgBExpr.getId)
    val cBindExprId        = mkStaticDep(cBindExpr.getId)
    val cBindNameId        = mkStaticDep(cBindName.getId)
    val plusExprId         = mkStaticDep(plusExpr.getId)
    val plusExprFnId       = mkStaticDep(plusExprFn.getId)
    val plusExprArgAId     = mkStaticDep(plusExprArgA.getId)
    val plusExprArgAExprId = mkStaticDep(plusExprArgAExpr.getId)
    val plusExprArgBId     = mkStaticDep(plusExprArgB.getId)
    val plusExprArgBExprId = mkStaticDep(plusExprArgBExpr.getId)
    val frobExprId         = mkStaticDep(frobExpr.getId)
    val frobFnId           = mkStaticDep(frobFn.getId)
    val frobArgAId         = mkStaticDep(frobArgA.getId)
    val frobArgAExprId     = mkStaticDep(frobArgAExpr.getId)
    val frobArgCId         = mkStaticDep(frobArgC.getId)
    val frobArgCExprId     = mkStaticDep(frobArgCExpr.getId)

    "correctly identify global symbol dependents" in {
      pending
    }

    "correctly identify local dependents" in {
      depInfo.get(fnId) shouldEqual Some(Set(methodId))
    }

//    "only store direct dependents for any given node" in {
//      pending
//    }
//
//    "correctly invalidate expressions on change" in {
//      pending
//    }
//
//    "associate the dependency info with every node in the IR" in {
//      pending
//    }
  }

//  "Dataflow analysis on expressions" should {
//    "properly update the analysis results" in {
//      pending
//    }
//  }
//
//  "Dataflow analysis" should {
//    "work properly for functions" in {
//      pending
//    }
//
//    "work properly for prefix applications" in {
//      pending
//    }
//
//    "work properly for forces" in {
//      pending
//    }
//
//    "work properly for names" in {
//      pending
//    }
//
//    "work properly for case expressions" in {
//      pending
//    }
//
//    "work properly for comments" in {
//      pending
//    }
//
//    "work properly for blocks" in {
//      pending
//    }
//
//    "work properly for bindings" in {
//      pending
//    }
//
//    "have the result data associated with literals" in {
//      pending
//    }
//  }
}
