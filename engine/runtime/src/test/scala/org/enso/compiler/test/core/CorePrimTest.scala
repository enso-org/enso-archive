package org.enso.compiler.test.core

import org.enso.compiler.test.CompilerTest
import org.enso.core.CoreGraph.{
  Link,
  LiteralStorage,
  Node,
  ParentStorage
}
import org.scalatest.BeforeAndAfterEach
import org.enso.graph.{Graph => PrimGraph}

/** This file tests the primitive, low-level operations on core.
  *
  * It does _not_ utilise the high-level API, and instead works directly with
  * the defined graph primitives.
  */
class CorePrimTest extends CompilerTest with BeforeAndAfterEach {

  // === Test Setup ===========================================================
  import org.enso.core.CoreGraph.CoreGraph
  import org.enso.core.CoreGraph.Link.Shape._
  import org.enso.core.CoreGraph.Node.Location._
  import org.enso.core.CoreGraph.Node.ParentLinks._

  // Reassignable mutable fixture elements
  implicit var graph: PrimGraph.GraphData[CoreGraph] = _
  implicit var literalStorage: LiteralStorage        = _
  implicit var parentStorage: ParentStorage          = _

  override def beforeEach(): Unit = {
    graph          = PrimGraph[CoreGraph]()
    literalStorage = LiteralStorage()
    parentStorage  = ParentStorage()
  }

  // === Tests for Links ======================================================

  val link = "A link"

  link should "only be equal to itself" in {
    val l1: Link[CoreGraph] = graph.addLink()
    val l2: Link[CoreGraph] = graph.addLink()

    l1 shouldEqual l1
    l1 should not equal l2
  }

  link should "have a source and a target" in {
    val l1: Link[CoreGraph]       = graph.addLink()
    val srcNode: Node[CoreGraph]  = graph.addNode()
    val destNode: Node[CoreGraph] = graph.addNode()

    l1.source = srcNode
    l1.target = destNode

    val expectedShape = Link.ShapeVal(srcNode, destNode)

    l1.shape shouldEqual expectedShape
  }

  // === Tests for Nodes ======================================================

  val node      = "A node"
  val nodeShape = "A node's shape"

  node should "only be equal to itself" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val n2: Node[CoreGraph] = graph.addNode()

    n1 shouldEqual n1
    n1 should not equal n2
  }

  node should "contain source location information" in {
    val n1: Node[CoreGraph] = graph.addNode()

    n1.sourceStart = 302
    n1.sourceEnd   = 364

    val expectedLocation = Node.LocationVal(302, 364)

    n1.location shouldEqual expectedLocation
  }

  node should "be able to have multiple parent edges" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val l1: Link[CoreGraph] = graph.addLink()
    val l2: Link[CoreGraph] = graph.addLink()
    val l3: Link[CoreGraph] = graph.addLink()

    l1.target = n1
    l2.target = n1
    l3.target = n1

    val parentIndices: Vector[Int] = Vector(l1.ix, l2.ix, l3.ix)
    n1.parents = parentIndices

    n1.parents shouldEqual parentIndices
    n1.parents.length shouldEqual 3
  }

  nodeShape should "be able to be empty" in {
    val n1: Node[CoreGraph] = graph.addNode()

    Node.setShape[Node.Shape.Empty](n1)

    val isEmpty = n1 match {
      case Node.Shape.Empty.any(_) => true
      case _                       => false
    }

    isEmpty shouldEqual true
  }

  nodeShape should "be able to represent a list cons cell" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val l1: Link[CoreGraph] = graph.addLink()
    val l2: Link[CoreGraph] = graph.addLink()

    Node.setShape[Node.Shape.List](n1)

    n1 match {
      case Node.Shape.List.any(n1) =>
        n1.head = l1
        n1.tail = l2

        n1.head shouldEqual l1
        n1.tail shouldEqual l2
      case _ => fail
    }
  }

  nodeShape should "be able to represent a nil cell" in {
    val n1: Node[CoreGraph] = graph.addNode()

    Node.setShape[Node.Shape.Nil](n1)

    val isNil = n1 match {
      case Node.Shape.Nil.any(_) => true
      case _                     => false
    }

    isNil shouldEqual true
  }

  nodeShape should "be able to represent a raw literal" in {
    val n1: Node[CoreGraph] = graph.addNode()

    Node.setShape[Node.Shape.RawLiteral](n1)

    n1 match {
      case Node.Shape.RawLiteral.any(n1) =>
        val literalText = "Literal Text"

        n1.literal = literalText

        n1.literal shouldEqual literalText
      case _ => fail
    }
  }

  nodeShape should "be able to represent a numeric literal" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val l1: Link[CoreGraph] = graph.addLink()

    Node.setShape[Node.Shape.NumericLiteral](n1)

    n1 match {
      case Node.Shape.NumericLiteral.any(n1) =>
        n1.number = l1

        n1.number shouldEqual l1
      case _ => fail
    }
  }

  nodeShape should "be able to represent a text literal" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val l1: Link[CoreGraph] = graph.addLink()

    Node.setShape[Node.Shape.TextLiteral](n1)

    n1 match {
      case Node.Shape.TextLiteral.any(n1) =>
        n1.text = l1

        n1.text shouldEqual l1
      case _ => fail
    }
  }
}
