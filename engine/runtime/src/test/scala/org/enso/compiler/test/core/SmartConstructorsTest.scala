package org.enso.compiler.test.core

import cats.data.NonEmptyList
import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.compiler.core.Core
import org.enso.compiler.test.CompilerTest
import org.scalatest.BeforeAndAfterEach
import org.enso.syntax.text.{Location => AstLocation}

class SmartConstructorsTest extends CompilerTest with BeforeAndAfterEach {

  // === Test Setup ===========================================================

  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._
  import Core._

  implicit var core: Core = _

  override def beforeEach(): Unit = {
    core = new Core()
  }

  // === Useful Constants =====================================================
  val blankLocation: CoreDef.Node.LocationVal[Graph] =
    CoreDef.Node.LocationVal(0, 0)

  // === Tests for Node Smart Constructors ====================================

  // === Tests for Node Utility Functions =====================================

  "Nodes for meta lists" should "be correctly identified" in {
    val emptyNode = Node.New.empty()
    val nilNode   = Node.New.metaNil()
    val consNode  = Node.New.metaList(emptyNode, nilNode).right.get

    Core.Node.Utility.isListNode(emptyNode) shouldEqual false
    Core.Node.Utility.isListNode(nilNode) shouldEqual true
    Core.Node.Utility.isListNode(consNode) shouldEqual true
  }

  "Core lists" should "be able to be constructed from arbitrary nodes" in {
    val emptyNode1 = Node.New.empty().wrapped
    val emptyNode2 = Node.New.empty().wrapped
    val emptyNode3 = Node.New.empty().wrapped

    val listOfOne = Core.Node.Utility.coreListFrom(emptyNode1)
    val listOfMany = Core.Node.Utility
      .coreListFrom(NonEmptyList(emptyNode1, List(emptyNode2, emptyNode3)))

    listOfOne.head.target shouldEqual emptyNode1

    listOfOne.tail.target match {
      case NodeShape.MetaNil.any(_) =>
      case _                        => fail
    }

    listOfMany.head.target shouldEqual emptyNode1

    listOfMany.tail.target match {
      case NodeShape.MetaList.any(e2) =>
        e2.head.target shouldEqual emptyNode2

        e2.tail.target match {
          case NodeShape.MetaList.any(e3) =>
            e3.head.target shouldEqual emptyNode3
            e3.tail.target match {
              case NodeShape.MetaNil.any(_) => succeed
              case _                        => fail
            }
          case _ => fail
        }
      case _ => fail
    }
  }

  // === Tests for Node Conversions ===========================================

  "AST locations" should "be converted losslessly to core locations" in {
    val startLoc = 1232
    val endLoc = 1337

    val astLoc = AstLocation(startLoc, endLoc)
    val coreLoc = CoreDef.Node.LocationVal(startLoc, endLoc)

    Node.Conversions.astLocationToNodeLocation(astLoc) shouldEqual coreLoc
  }

  // === Tests for Link Smart Constructors ====================================

  "Links" should "be able to be made with a source and target" in {
    val n1 = Core.Node.New.empty()
    val n2 = Core.Node.New.empty()

    val link = Core.Link.New.connected(n1, n2)

    link.source shouldEqual n1
    link.target shouldEqual n2
  }

  "Links" should "be able to be made with only a source" in {
    val sourceNode = Core.Node.New.metaNil()

    val link = Core.Link.New.disconnected(sourceNode)

    link.source shouldEqual sourceNode

    link.target match {
      case NodeShape.Empty.any(_) => succeed
      case _                      => fail
    }
  }

}
