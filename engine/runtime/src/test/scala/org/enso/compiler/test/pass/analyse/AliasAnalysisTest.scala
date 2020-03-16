package org.enso.compiler.test.pass.analyse

import com.sun.corba.se.impl.orbutil.ObjectStreamClassUtil_1_3
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Link, Occurrence}
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

  // TODO [AA] Some property-based testing using ScalaCheck?

  "The analysis scope" should {
    val graph = new Graph()

    val flatScope = new Graph.Scope()

    val complexScope        = new Graph.Scope()
    val child1              = complexScope.addChild()
    val child2              = complexScope.addChild()
    val childOfChild        = child1.addChild()
    val childOfChildOfChild = childOfChild.addChild()

    val aDefId = graph.nextId()
    val aDef   = Occurrence.Def(aDefId, "a")

    val bDefId = graph.nextId()
    val bDef   = Occurrence.Def(bDefId, "b")

    val aUseId = graph.nextId()
    val aUse   = Occurrence.Use(aUseId, "a")

    val bUseId = graph.nextId()
    val bUse   = Occurrence.Use(bUseId, "b")

    val cUseId = graph.nextId()
    val cUse   = Occurrence.Use(cUseId, "c")

    // Add occurrences to the scopes
    complexScope.addOccurrence(aDef)
    child1.addOccurrence(cUse)
    childOfChild.addOccurrence(bDef)
    childOfChild.addOccurrence(bUse)
    childOfChildOfChild.addOccurrence(aUse)

    "Have a number of scopes of 1 without children" in {
      flatScope.numScopes shouldEqual 1
    }

    "Have a nesting level of 1 without children" in {
      flatScope.nesting shouldEqual 1
    }

    "Have the correct number of scopes with children" in {
      complexScope.numScopes shouldEqual 5
    }

    "Have the correct nesting depth with children" in {
      complexScope.nesting shouldEqual 4
    }

    "Allow correctly getting the n-th parent" in {
      childOfChildOfChild.nThParent(2) shouldEqual Some(child1)
    }

    "Return `None` for nonexistent parents" in {
      childOfChildOfChild.nThParent(10) shouldEqual None
    }

    "Find the occurrence for an ID in the current scope if it exists" in {
      complexScope.occurrence(aDefId) shouldEqual Some(aDef)
    }

    "Find the occurrence for an name in the current scope if it exists" in {
      complexScope.occurrence(aDef.symbol) shouldEqual Some(aDef)
    }

    "Find no occurrences if they do not exist" in {
      complexScope.occurrence(cUseId) shouldEqual None
    }

    "Correctly resolve usage links where they exist" in {
      childOfChild.resolveUsage(bUse) shouldEqual Some(Link(bUseId, 0, bDefId))
      childOfChildOfChild.resolveUsage(aUse) shouldEqual Some(
        Link(aUseId, 3, aDefId)
      )
    }

    "Correctly find the scope where a given ID occurs" in {
      complexScope.scopeForId(aUseId) shouldEqual Some(childOfChildOfChild)
    }

    "Correctly find the scopes in which a given symbol occurs" in {
      complexScope.scopesForSymbol[Occurrence.Def]("a").length shouldEqual 1
      complexScope.scopesForSymbol[Occurrence.Use]("a").length shouldEqual 1

      complexScope.scopesForSymbol[Occurrence]("a").length shouldEqual 2

      complexScope.scopesForSymbol[Occurrence]("a") shouldEqual List(
        complexScope,
        childOfChildOfChild
      )
    }
  }

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
