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

    val frobnicateSymbol = mkDynamicDep("frobnicate")
    val ioSymbol         = mkDynamicDep("IO")
    val printlnSymbol    = mkDynamicDep("println")
    val plusSymbol       = mkDynamicDep("+")

    val fooMethod =
      ir.bindings.head.asInstanceOf[IR.Module.Scope.Definition.Method]
    val fn     = fooMethod.body.asInstanceOf[IR.Function.Lambda]
    val fnBody = fn.body.asInstanceOf[IR.Expression.Block]
    val printlnExpr =
      fnBody.expressions.head.asInstanceOf[IR.Application.Prefix]
    val printlnFn = printlnExpr.function.asInstanceOf[IR.Name.Literal]
    val ioArgInPrintln =
      printlnExpr.arguments.head.asInstanceOf[IR.CallArgument.Specified]
    val bArgInPrintln =
      printlnExpr.arguments(1).asInstanceOf[IR.CallArgument.Specified]
    val cBinding = fnBody.expressions(1).asInstanceOf[IR.Expression.Binding]
    val aAddB    = cBinding.expression.asInstanceOf[IR.Application.Prefix]
    val retVal   = fnBody.returnValue.asInstanceOf[IR.Application.Prefix]

    val methodId         = mkStaticDep(fooMethod.getId)
    val functionId       = mkStaticDep(fn.getId)
    val aArgId           = mkStaticDep(fn.arguments.head.getId)
    val bArgId           = mkStaticDep(fn.arguments(1).getId)
    val fnBodyId         = mkStaticDep(fnBody.getId)
    val printlnExprId    = mkStaticDep(printlnExpr.getId)
    val printlnFnId      = mkStaticDep(printlnExpr.function.getId)
    val ioArgInPrintlnId = mkStaticDep(ioArgInPrintln.getId)
    val ioUseInPrintlnId = mkStaticDep(ioArgInPrintln.value.getId)
    val bArgInPrintlnId  = mkStaticDep(bArgInPrintln.getId)
    val bUseInPrintlnId  = mkStaticDep(bArgInPrintln.value.getId)
    val cBindingId       = mkStaticDep(cBinding.getId)
    val cNameId          = mkStaticDep(cBinding.name.getId)
    val aAddBId          = mkStaticDep(aAddB.getId)
    val aUseAddId        = mkStaticDep(aAddB.arguments.head.getId)
    val bUseAddId        = mkStaticDep(aAddB.arguments(1).getId)
    val retValId         = mkStaticDep(retVal.getId)
    val frobnicateId     = mkStaticDep(retVal.function.getId)
    val aUseRetId        = mkStaticDep(retVal.arguments.head.getId)
    val cUseRetId        = mkStaticDep(retVal.arguments(1).getId)

    "correctly identify global symbol dependents" in {
      pending
    }

//    "correctly identify local dependents" in {
//      pending
//    }
//
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
