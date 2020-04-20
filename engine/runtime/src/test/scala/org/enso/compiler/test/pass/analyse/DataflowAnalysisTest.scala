package org.enso.compiler.test.pass.analyse

import java.util.UUID

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.DataflowAnalysis.DependenciesImpl
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

  /** Generates a new random [[UUID]].
    *
    * @return a random [[UUID]].
    */
  def genUUID: UUID = UUID.randomUUID()

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

  "Dataflow analysis on modules" should {}

  "Dataflow analysis on expressions" should {}
}
