package org.enso.compiler.test.core

import cats.data.NonEmptyList
import org.enso.compiler.core.Core
import org.enso.compiler.core.Core.Node.{Constants, Utility}
import org.enso.compiler.test.CompilerTest
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.syntax.text.{AST, Location => AstLocation}
import org.enso.compiler.test.CompilerTest

// TODO [AA] Tests of the actual core usage in semi-realistic scenarios:
//  - Diamond
//  - Multi-level tree
//  - Larger diamonds
//  - Cycle
//  - Linked List
//  - Parent Link walk to same place
//  - etc.
class CoreTest extends CompilerTest {

  // === Test Setup ===========================================================

  import Core._
  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._
  import PrimGraph.VariantCast

  // === Useful Constants =====================================================

  val constantLocationStart = 201
  val constantLocationEnd   = 1337
  val dummyLocation: Core.Location =
    CoreDef.Node.LocationVal(constantLocationStart, constantLocationEnd)

  // === More Complex Graph Shape Tests =======================================

  // TODO [AA] Once deletion and replacement functions exist, expand these tests
  //  to check that the shapes behave properly under usage of such functions.

  "Diamonds constructed on the graph" should {
    implicit val core: Core = new Core()

    val fnName       = Node.New.Name("foo", dummyLocation)
    val binding1Name = Node.New.Name("a", dummyLocation)
    val binding1     = Node.New.Binding(binding1Name, fnName, dummyLocation)
    val binding2Name = Node.New.Name("b", dummyLocation)
    val binding2     = Node.New.Binding(binding2Name, fnName, dummyLocation)
    val bindingsList = Utility.coreListFrom(binding1)

    val block = Node.New.Block(bindingsList, binding2, dummyLocation).right.get

    "have multiple parents for the node at the bottom of the diamond" in {
      fnName.parents.size shouldEqual 2
      fnName.parents should contain(binding1.expression.ix)
      fnName.parents should contain(binding2.expression.ix)
    }

    "allow traversals through both paths" in {
      val fnNameViaLeftPath = block.expressions.target
        .unsafeAs[NodeShape.MetaList]
        .head
        .target
        .unsafeAs[NodeShape.Binding]
        .expression
        .target

      val fnNameViaRightPath =
        block.returnVal.target.unsafeAs[NodeShape.Binding].expression.target

      fnNameViaLeftPath shouldEqual fnNameViaRightPath
    }
  }
}
