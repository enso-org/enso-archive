package org.enso.compiler.test.core

import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.compiler.core.Core
import org.enso.compiler.test.CompilerTest
import org.scalatest.BeforeAndAfterEach

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
