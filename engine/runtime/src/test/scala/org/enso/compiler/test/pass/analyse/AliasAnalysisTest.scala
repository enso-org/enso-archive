package org.enso.compiler.test.pass.analyse

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
    child2.addOccurrence(cUse)
    childOfChild.addOccurrence(bDef)
    childOfChild.addOccurrence(bUse)
    childOfChildOfChild.addOccurrence(aUse)

    "have a number of scopes of 1 without children" in {
      flatScope.numScopes shouldEqual 1
    }

    "have a nesting level of 1 without children" in {
      flatScope.nesting shouldEqual 1
    }

    "have the correct number of scopes with children" in {
      complexScope.numScopes shouldEqual 5
    }

    "have the correct nesting depth with children" in {
      complexScope.nesting shouldEqual 4
    }

    "allow correctly getting the n-th parent" in {
      childOfChildOfChild.nThParent(2) shouldEqual Some(child1)
    }

    "return `None` for nonexistent parents" in {
      childOfChildOfChild.nThParent(10) shouldEqual None
    }

    "find the occurrence for an ID in the current scope if it exists" in {
      complexScope.occursInThisScope(aDefId) shouldEqual Some(aDef)
    }

    "find the occurrence for an name in the current scope if it exists" in {
      complexScope.occursInThisScope(aDef.symbol) shouldEqual Set(aDef)
    }

    "find no occurrences if they do not exist" in {
      complexScope.occursInThisScope(cUseId) shouldEqual None
    }

    "correctly resolve usage links where they exist" in {
      childOfChild.resolveUsage(bUse) shouldEqual Some(Link(bUseId, 0, bDefId))
      childOfChildOfChild.resolveUsage(aUse) shouldEqual Some(
        Link(aUseId, 3, aDefId)
      )
    }

    "correctly find the scope where a given ID occurs" in {
      complexScope.scopeForId(aUseId) shouldEqual Some(childOfChildOfChild)
    }

    "correctly find the scopes in which a given symbol occurs" in {
      complexScope.scopesForSymbol[Occurrence.Def]("a").length shouldEqual 1
      complexScope.scopesForSymbol[Occurrence.Use]("a").length shouldEqual 1

      complexScope.scopesForSymbol[Occurrence]("a").length shouldEqual 2

      complexScope.scopesForSymbol[Occurrence]("a") shouldEqual List(
        complexScope,
        childOfChildOfChild
      )
    }

    "return the correct set of symbols" in {
      complexScope.symbols shouldEqual Set("a", "b", "c")
    }
  }

  "The Aliasing graph" should {
    val graph = new Graph()

    val rootScope  = graph.rootScope
    val childScope = rootScope.addChild()

    val aDefId = graph.nextId()
    val aDef   = Occurrence.Def(aDefId, "a")

    val bDefId = graph.nextId()
    val bDef   = Occurrence.Def(bDefId, "b")

    val aUse1Id = graph.nextId()
    val aUse1   = Occurrence.Use(aUse1Id, "a")

    val aUse2Id = graph.nextId()
    val aUse2   = Occurrence.Use(aUse2Id, "a")

    val cUseId = graph.nextId()
    val cUse   = Occurrence.Use(cUseId, "c")

    rootScope.addOccurrence(aDef)
    rootScope.addOccurrence(aUse1)
    rootScope.addOccurrence(bDef)

    childScope.addOccurrence(aUse2)
    childScope.addOccurrence(cUse)

    val use1Link = graph.resolveUsage(aUse1)
    val use2Link = graph.resolveUsage(aUse2)
    val cUseLink = graph.resolveUsage(cUse)

    "generate monotonically increasing identifiers" in {
      val ids       = List.fill(100)(graph.nextId())
      var currentId = ids.head - 1

      ids.forall(id => {
        currentId += 1
        currentId == id
      }) shouldEqual true
    }

    "correctly resolve usages where possible" in {
      use1Link shouldBe defined
      use2Link shouldBe defined
      cUseLink shouldBe empty

      use1Link.foreach { link =>
        {
          link.source shouldEqual aUse1Id
          link.target shouldEqual aDefId
          link.scopeCount shouldEqual 0
        }
      }

      use2Link.foreach { link =>
        {
          link.source shouldEqual aUse2Id
          link.target shouldEqual aDefId
          link.scopeCount shouldEqual 1
        }
      }
    }

    "gather all links for a given ID correctly" in {
      val linksForA = graph.linksFor(aDefId)
      val linksForC = graph.linksFor(cUseId)

      linksForA.size shouldEqual 2
      linksForC shouldBe empty

      linksForA should contain(use1Link.get)
      linksForA should contain(use2Link.get)
    }

    "find the scope where a given ID is defined" in {
      val scopeForA       = graph.scopeFor(aDefId)
      val scopeForUndefId = graph.scopeFor(100)

      scopeForA shouldBe defined
      scopeForUndefId shouldBe empty

      scopeForA shouldEqual Some(rootScope)
    }

    "find all scopes where a given symbol occurs" in {
      val aDefs = graph.scopesFor[Occurrence.Def]("a")
      val aUses = graph.scopesFor[Occurrence.Use]("a")
      val aOccs = graph.scopesFor[Occurrence]("a")
      val dOccs = graph.scopesFor[Occurrence]("d")

      aDefs.length shouldEqual 1
      aUses.length shouldEqual 2
      aOccs.length shouldEqual 2
      dOccs.length shouldEqual 0

      aDefs shouldEqual List(rootScope)
      aUses shouldEqual List(rootScope, childScope)
      aOccs shouldEqual List(rootScope, childScope)
    }

    "correctly determine the number of scopes in the graph" in {
      graph.numScopes shouldEqual 2
    }

    "correctly determine the maximum level of nesting in the graph" in {
      graph.nesting shouldEqual 2
    }

    "correctly determines whether an occurrence shadows other bindings" in {
      graph.shadows(aDefId) shouldEqual true
      graph.shadows("a") shouldEqual true
      graph.shadows(aUse1Id) shouldEqual false
      graph.shadows("c") shouldEqual false
    }

    "correctly determine all symbols that occur in the graph" in {
      graph.symbols shouldEqual Set("a", "b", "c")
    }
  }

  "Alias analysis" should {
    val ir =
      """
        |main =
        |    a = 2 + 2
        |    b = a * a
        |""".stripMargin.preprocess

    "do the thing" in {
//      println(
//        AliasAnalysis
//          .runModule(ir)
//          .bindings
//          .map(_.getMetadata[AliasAnalysis.Info].get)
//      )
    }

//    val ir =
//      """
//        |main =
//        |    a = 2 + 2
//        |    b = a * a
//        |    c = a -> a + b
//        |
//        |    IO.println 2.c
//        |""".stripMargin.preprocess
//
//    "do the thing" in {
//      println(
//        AliasAnalysis
//          .runModule(ir)
//          .bindings
//          .map(_.getMetadata[AliasAnalysis.Info].get)
//      )
//    }
//
//    val ir2 =
//      """
//        |type MyAtom a b=c (c = a + b)
//        |""".stripMargin.preprocess
//
//    "do the other thing" in {
//      println(
//        AliasAnalysis
//          .runModule(ir2)
//          .bindings
//          .map(_.getMetadata[AliasAnalysis.Info].get)
//      )
//    }
  }
}
