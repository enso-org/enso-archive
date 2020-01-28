package org.enso.compiler.test.core

import org.enso.compiler.test.CompilerTest
import org.enso.core.CoreGraph.{Link, Node, ParentStorage, StringLiteralStorage}
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
  import org.enso.core.CoreGraph.Node._
  import org.enso.core.CoreGraph.Link._

  // Reassignable mutable fixture elements
  implicit var graph: PrimGraph.GraphData[CoreGraph] = _
  implicit var stringStorage: StringLiteralStorage = _
  implicit var parentStorage: ParentStorage = _

  override def beforeEach(): Unit = {
    graph = PrimGraph[CoreGraph]()
    stringStorage = StringLiteralStorage()
    parentStorage = ParentStorage()
  }

  // === Tests for Nodes ======================================================

  "A node" should "only be equal to itself" in {
    val n1: Node[CoreGraph] = graph.addNode()
    val n2: Node[CoreGraph] = graph.addNode()

    n1 shouldEqual n1
    n1 should not equal n2
  }

  // === Tests for Links ======================================================

  "A link" should "only be equal to itself" in {
    val l1: Link[CoreGraph] = graph.addLink()
    val l2: Link[CoreGraph] = graph.addLink()

    l1 shouldEqual l1
    l1 should not equal l2
  }
}
