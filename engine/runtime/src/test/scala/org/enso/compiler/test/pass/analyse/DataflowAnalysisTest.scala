package org.enso.compiler.test.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.DataflowAnalysis.{
  Dependencies,
  DependenciesImpl
}
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

  "Dependency descriptions" should {
    "allow direct access to the underlying storage" in {
      val deps = new DependenciesImpl.Concrete[Int, String]

      deps.dependencies shouldBe empty
    }

    "allow users to get the dependents of a given identifier" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val str  = "Foo"

      deps(1) = str
      deps(1) shouldEqual str
    }

    "throw if an unsafe access is used for a nonexistent identifier" in {
      val deps = new DependenciesImpl.Concrete[Int, String]

      a[NoSuchElementException] should be thrownBy deps(1)
    }

    "allow users to safely get the dependents of a given identifier" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      deps(1) = "Str"

      deps.get(1) shouldBe defined
      deps.get(2) should not be defined
    }

    "allow users to update the dependent sets" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val str  = "Str"

      deps(1) = str

      deps(1) += str
      deps(1) shouldEqual (str + str)
    }

    "allow users to remove identifiers" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val str  = "Str"

      deps(1) = str
      deps.remove(1) shouldEqual Some(str)
    }

    "allow users to check predicates against the dependency info" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val id   = 1

      deps(id) = "Str"
      deps.exists((ident, _) => id == ident) shouldBe true
    }

    "allow users to map over the dependency info" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val id   = 1
      val id2  = 2
      val str  = "Str"
      deps(id)  = str
      deps(id2) = str + str

      val newDeps = deps.map((x, y) => (x, y.length))

      newDeps(id) shouldEqual str.length
      newDeps(id2) shouldEqual (str + str).length
    }

    "allow users to filter the dependency info" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val id   = 1
      val id2  = 2
      val str  = "Str"
      deps(id)  = str
      deps(id2) = str + str

      val newDeps = deps.filter((_, string) => string.length < 5)

      newDeps.get(id) shouldBe defined
      newDeps.get(id2) should not be defined
    }

    "allow users to fold over the dependency info" in {
      val deps = new DependenciesImpl.Concrete[Int, String]
      val id   = 1
      val id2  = 2
      val str  = "Str"
      deps(id)  = str
      deps(id2) = str + str

      deps.fold(0) {
        case (num, (_, rStr)) => num + rStr.length
      } shouldEqual (str.length * 3)
    }
  }

  "Dataflow metadata for scopes" should {}

  "Dataflow metadata for modules" should {
    "allow querying for ids that should be invalidated on symbol change" in {}

    "allow querying for ids that should be invalidated on id change" in {
      val scope = new Dependencies.Scope
      val ids   = List.fill(5)(genID)

      scope(ids.head) = Set(ids(1), ids(2))
      scope(ids(2))   = Set(ids(3), ids(4))
      scope(ids(4))   = Set(ids(1), ids.head)

      scope.shouldInvalidateWhenChanging(ids.head) shouldEqual Set(
        ids(1),
        ids(2),
        ids(3),
        ids(4),
        ids.head
      )
    }

    "allow users to combine the information from multiple modules" in {
      val module1 = new Dependencies.Module
      val module2 = new Dependencies.Module

      val symbol1 = "foo"
      val symbol2 = "bar"
      val symbol3 = "baz"

      val symbol1DependentIdsInModule1 = Set(genID, genID)
      val symbol2DependentIdsInModule1 = Set(genID, genID)
      val symbol1DependentIdsInModule2 = Set(genID, genID)
      val symbol3DependentIdsInModule2 = Set(genID)

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

  "Dataflow analysis on modules" should {}

  "Dataflow analysis on expressions" should {}
}
