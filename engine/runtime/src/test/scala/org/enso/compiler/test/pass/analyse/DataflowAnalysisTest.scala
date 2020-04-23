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
import org.enso.interpreter.runtime.scope.LocalScope
import org.scalatest.Assertion

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

  /** Adds an extension method to check whether the target IR node has
    * associated dataflow analysis metadata.
    *
    * @param ir the IR node to check
    */
  implicit class HasDependencyInfo(ir: IR) {

    /** Checks if [[ir]] has associated [[DataflowAnalysis.Metadata]].
      *
      * @return `true` if [[ir]] has the associated metadata, otherwise `false`
      */
    def hasDependencyInfo: Assertion = {
      ir.getMetadata[DataflowAnalysis.Metadata] shouldBe defined
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
    val fn = method.body.asInstanceOf[IR.Function.Lambda]
    val fnArgThis =
      fn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified]
    val fnArgA = fn.arguments(1).asInstanceOf[IR.DefinitionArgument.Specified]
    val fnArgB = fn.arguments(2).asInstanceOf[IR.DefinitionArgument.Specified]
    val fnBody = fn.body.asInstanceOf[IR.Expression.Block]

    // The `IO.println` expression
    val printlnExpr =
      fnBody.expressions.head.asInstanceOf[IR.Application.Prefix]
    val printlnFn = printlnExpr.function.asInstanceOf[IR.Name.Literal]
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
    val printlnFnId        = mkStaticDep(printlnFn.getId)
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

    "correctly identify global symbol direct dependents" in {
      depInfo.getDirect(frobnicateSymbol) shouldEqual Some(Set(frobFnId))
      depInfo.getDirect(ioSymbol) shouldEqual Some(Set(printlnArgIOExprId))
      depInfo.getDirect(printlnSymbol) shouldEqual Some(Set(printlnFnId))
      depInfo.getDirect(plusSymbol) shouldEqual Some(Set(plusExprFnId))
    }

    "correctly identify global symbol indirect dependents" in {
      depInfo.get(frobnicateSymbol) shouldEqual Some(
        Set(frobFnId, frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(ioSymbol) shouldEqual Some(
        Set(printlnArgIOExprId, printlnArgIOId, printlnExprId)
      )
      depInfo.get(printlnSymbol) shouldEqual Some(
        Set(printlnFnId, printlnExprId)
      )
      depInfo.get(plusSymbol) shouldEqual Some(
        Set(
          plusExprFnId,
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
    }

    "correctly identify local direct dependents" in {
      depInfo.getDirect(fnId) shouldEqual Some(Set(methodId))
      depInfo.getDirect(fnArgAId) shouldEqual Some(
        Set(plusExprArgAExprId, frobArgAExprId)
      )
      depInfo.getDirect(fnArgBId) shouldEqual Some(
        Set(printlnArgBExprId, plusExprArgBExprId)
      )
      depInfo.getDirect(fnBodyId) shouldEqual Some(Set(fnId))

      // The `IO.println` expression
      depInfo.getDirect(printlnExprId) should not be defined
      depInfo.getDirect(printlnFnId) shouldEqual Some(Set(printlnExprId))
      depInfo.getDirect(printlnArgIOId) shouldEqual Some(Set(printlnExprId))
      depInfo.getDirect(printlnArgIOExprId) shouldEqual Some(
        Set(printlnArgIOId)
      )
      depInfo.getDirect(printlnArgBId) shouldEqual Some(Set(printlnExprId))
      depInfo.getDirect(printlnArgBExprId) shouldEqual Some(
        Set(printlnArgBId)
      )

      // The `c = ` expression
      depInfo.getDirect(cBindExprId) shouldEqual Some(Set(frobArgCExprId))
      depInfo.getDirect(cBindNameId) shouldEqual Some(Set(cBindExprId))
      depInfo.getDirect(plusExprId) shouldEqual Some(Set(cBindExprId))
      depInfo.getDirect(plusExprFnId) shouldEqual Some(Set(plusExprId))
      depInfo.getDirect(plusExprArgAId) shouldEqual Some(Set(plusExprId))
      depInfo.getDirect(plusExprArgAExprId) shouldEqual Some(
        Set(plusExprArgAId)
      )
      depInfo.getDirect(plusExprArgBId) shouldEqual Some(Set(plusExprId))
      depInfo.getDirect(plusExprArgBExprId) shouldEqual Some(
        Set(plusExprArgBId)
      )

      // The `frobnicate` expression
      depInfo.getDirect(frobExprId) shouldEqual Some(Set(fnBodyId))
      depInfo.getDirect(frobFnId) shouldEqual Some(Set(frobExprId))
      depInfo.getDirect(frobArgAId) shouldEqual Some(Set(frobExprId))
      depInfo.getDirect(frobArgAExprId) shouldEqual Some(Set(frobArgAId))
      depInfo.getDirect(frobArgCId) shouldEqual Some(Set(frobExprId))
      depInfo.getDirect(frobArgCExprId) shouldEqual Some(Set(frobArgCId))
    }

    "correctly identify local indirect dependents" in {
      depInfo.get(fnId) shouldEqual Some(Set(methodId))
      depInfo.get(fnArgAId) shouldEqual Some(
        Set(
          plusExprArgAExprId,
          plusExprArgAId,
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobArgAExprId,
          frobArgAId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(fnArgBId) shouldEqual Some(
        Set(
          printlnArgBExprId,
          printlnArgBId,
          printlnExprId,
          plusExprArgBExprId,
          plusExprArgBId,
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(fnBodyId) shouldEqual Some(Set(fnId, methodId))

      // The `IO.println` expression
      depInfo.get(printlnExprId) should not be defined
      depInfo.get(printlnFnId) shouldEqual Some(Set(printlnExprId))
      depInfo.get(printlnArgIOId) shouldEqual Some(Set(printlnExprId))
      depInfo.get(printlnArgIOExprId) shouldEqual Some(
        Set(printlnArgIOId, printlnExprId)
      )
      depInfo.get(printlnArgBId) shouldEqual Some(Set(printlnExprId))
      depInfo.get(printlnArgBExprId) shouldEqual Some(
        Set(printlnArgBId, printlnExprId)
      )

      // The `c = ` expression
      depInfo.get(cBindExprId) shouldEqual Some(
        Set(frobArgCExprId, frobArgCId, frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(cBindNameId) shouldEqual Some(
        Set(
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(plusExprId) shouldEqual Some(
        Set(
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(plusExprFnId) shouldEqual Some(
        Set(
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(plusExprArgAId) shouldEqual Some(
        Set(
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(plusExprArgAExprId) shouldEqual Some(
        Set(
          plusExprArgAId,
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )

      depInfo.get(plusExprArgBId) shouldEqual Some(
        Set(
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(plusExprArgBExprId) shouldEqual Some(
        Set(
          plusExprArgBId,
          plusExprId,
          cBindExprId,
          frobArgCExprId,
          frobArgCId,
          frobExprId,
          fnBodyId,
          fnId,
          methodId
        )
      )

      // The `frobnicate` expression
      depInfo.get(frobExprId) shouldEqual Some(
        Set(
          fnBodyId,
          fnId,
          methodId
        )
      )
      depInfo.get(frobFnId) shouldEqual Some(
        Set(frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(frobArgAId) shouldEqual Some(
        Set(frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(frobArgAExprId) shouldEqual Some(
        Set(frobArgAId, frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(frobArgCId) shouldEqual Some(
        Set(frobExprId, fnBodyId, fnId, methodId)
      )
      depInfo.get(frobArgCExprId) shouldEqual Some(
        Set(frobArgCId, frobExprId, fnBodyId, fnId, methodId)
      )
    }

    "associate the dependency info with every node in the IR" in {
      ir.hasDependencyInfo
      method.hasDependencyInfo
      fn.hasDependencyInfo
      fnArgThis.hasDependencyInfo
      fnArgA.hasDependencyInfo
      fnArgB.hasDependencyInfo
      fnBody.hasDependencyInfo

      printlnExpr.hasDependencyInfo
      printlnFn.hasDependencyInfo
      printlnArgIO.hasDependencyInfo
      printlnArgIOExpr.hasDependencyInfo
      printlnArgB.hasDependencyInfo
      printlnArgBExpr.hasDependencyInfo

      cBindExpr.hasDependencyInfo
      cBindName.hasDependencyInfo
      plusExpr.hasDependencyInfo
      plusExprFn.hasDependencyInfo
      plusExprArgA.hasDependencyInfo
      plusExprArgAExpr.hasDependencyInfo
      plusExprArgB.hasDependencyInfo
      plusExprArgBExpr.hasDependencyInfo

      frobExpr.hasDependencyInfo
      frobFn.hasDependencyInfo
      frobArgA.hasDependencyInfo
      frobArgAExpr.hasDependencyInfo
      frobArgC.hasDependencyInfo
      frobArgCExpr.hasDependencyInfo
    }
  }

  "Dataflow analysis on expressions" should {
    implicit val inlineContext =
      InlineContext(localScope = Some(LocalScope.root))

    val ir =
      """
        |x y = x -> x + y
        |""".stripMargin.preprocessExpression.get.analyse

    val depInfo = ir.getMetadata[DataflowAnalysis.Metadata].get

    "properly update the analysis results" in {
      pending
    }
  }

//  "Dataflow analysis" should {
//    "work properly for functions" in {
//      pending // TODO [AA] Default arguments
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
