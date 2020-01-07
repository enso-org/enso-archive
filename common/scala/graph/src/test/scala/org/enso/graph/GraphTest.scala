package org.enso.graph

import org.enso.graph.{Graph => PrimGraph}
import org.enso.graph.definition.Macro.{component, field}
import org.scalatest.{FlatSpec, Matchers}
import shapeless.{::, HNil}

class GraphTest extends FlatSpec with Matchers {
  object GraphImpl {

    // ========================================================================
    // === Component Definitions ==============================================
    // ========================================================================

    // === Node ===
    @component case class Nodes() { type Node[G <: PrimGraph] }

    // === Edge ===
    @component case class Edges() { type Edge[G <: PrimGraph] }

    // ========================================================================
    // === Component Field Definitions ========================================
    // ========================================================================

    object Node {

      // === Node Shape ===
      @field object Shape {
        type G = PrimGraph
        case class Null()
        case class App(fn: Edge[G], argTest: Edge[G])
      }

      // === ParentLink ===
      @field case class ParentLink[G <: PrimGraph](parent: Edge[G])
    }

    object Edge {

      // === Edge Shape ===
      @field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])
    }

    // ========================================================================
    // === Example Graph Implementation =======================================
    // ========================================================================

    case class Location (start: Int, end: Int)

    case class Graph() extends PrimGraph

    implicit def components = new PrimGraph.Component.List[Graph] {
      type Out = Nodes :: Edges :: HNil
    }

    implicit def nodeFields =
      new PrimGraph.Component.Field.List[Graph, Nodes] {
        type Out = Node.Shape :: Node.ParentLink :: HNil
      }

    implicit def edgeFields =
      new PrimGraph.Component.Field.List[Graph, Edges] {
        type Out = Edge.Shape :: HNil
      }
  }

  // ==========================================================================
  // === Example Graph Usage ==================================================
  // ==========================================================================

  import GraphImpl.Edge.Shape._
  import GraphImpl.Node.ParentLink._
  import GraphImpl.Node.Shape.App._
  import GraphImpl._

  implicit val graph: PrimGraph.GraphData[GraphImpl.Graph] =
    PrimGraph[GraphImpl.Graph]();

  val n1: Node[Graph] = graph.addNode()
  val n2: Node[Graph] = graph.addNode()
  val n3: Node[Graph] = graph.addNode()

  val e1: Edge[Graph] = graph.addEdge()
  e1.source = n1
  e1.target = n2

  n1.parent = PrimGraph.Component.Ref(1)
  n2.parent = PrimGraph.Component.Ref(2)
  n3.parent = PrimGraph.Component.Ref(3)

  // This is just dirty and very unsafe way of changing `n1` to be App!
  graph.unsafeWriteField[Nodes, GraphImpl.Node.Shape](n1.ix, 0, 1)

  // ==========================================================================
  // === Tests ================================================================
  // ==========================================================================

  "Matching on variants" should "work properly" in {
    val typeResult = n1 match {
      case GraphImpl.Node.Shape.Null.any(n @ _) => "Null"
      case GraphImpl.Node.Shape.App.any(n1 @ _) => "App1"
      case GraphImpl.Node.Shape.App(_, _)       => "App2"
    }

    typeResult shouldEqual "App1"
  }

  "Matching on variants" should "refine the variant type" in {
    val refinedResult = n1 match {
      case GraphImpl.Node.Shape.App.any(n1) => n1.fn
    }

    refinedResult shouldEqual 1
  }

  "Component fields" can "be accessed properly" in {
    e1.source shouldEqual n1
    e1.target shouldEqual n2
  }

  "The graph" should "be mutable" in {
    val e2: Edge[Graph] = graph.addEdge()
    e2.source = n1
    e2.target = n2

    e2.source shouldEqual n1

    e2.source = n3

    e2.source shouldEqual n3
  }
}
