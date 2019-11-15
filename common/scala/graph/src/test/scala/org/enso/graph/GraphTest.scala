package org.enso.graph

import org.enso.graph.Graph.Component
import org.enso.graph.GraphComponents.{Edge, Edges, Node, Nodes}
import org.enso.graph.definition.Macro.field
import org.scalatest.{FlatSpec, Matchers}
import shapeless.{::, HNil}

class GraphTest extends FlatSpec with Matchers {
  object Impl {

    // ==========================================================================
    // === Component definitions ================================================
    // ==========================================================================

    object Node {

      // ==================
      // === Node Shape ===
      // ==================

      @field object Shape {
        case class Null()
        case class App[G <: Graph](fn: Edge[G], argTest: Edge[G])
      }

      // ==================
      // === ParentLink ===
      // ==================

      @field case class ParentLink[G <: Graph](parent: Edge[G])
    }

    object Edge {

      // ==================
      // === Edge Shape ===
      // ==================

      @field case class Shape[G <: Graph](source: Node[G], target: Node[G])
    }

    // ==========================================================================
    // === Example Graph Implementation =========================================
    // ==========================================================================

    case class MyGraph() extends Graph

    implicit def components = new Graph.Component.List[MyGraph] {
      type Out = Nodes :: Edges :: HNil
    }

    implicit def nodeFields = new Graph.Component.Field.List[MyGraph, Nodes] {
      type Out = Node.Shape :: Node.ParentLink :: HNil
    }

    implicit def edgeFields = new Graph.Component.Field.List[MyGraph, Edges] {
      type Out = Edge.Shape :: HNil
    }
  }

  "Test graph implementations" should "work properly" in {
    import Impl.Edge.Shape._
    import Impl.Node.ParentLink._
    import GraphComponents._

    implicit val graph = Graph[Impl.MyGraph]();

    val n1 = graph.addNode()
    val n2 = graph.addNode()
    val n3 = graph.addNode()

    val e1 = graph.addEdge()
    e1.source = n1
    e1.target = n2

    n1.parent = Component.Ref(1)
    n2.parent = Component.Ref(2)
    n3.parent = Component.Ref(3)

    // This is just dirty and very unsafe way of changing `n1` to be App!
    graph.unsafeWriteField[Nodes, Impl.Node.Shape](n1.ix, 0, 1)

//    n1 match {
//      case Impl.Node.Shape.Null.any(n @ _) => {
//        println("Null!")
//      }
//      case Impl.Node.Shape.App.any(app) => {
//        println("App!")
//        println(app.fn)
//        println(app.parent)
//      }
//      case Impl.Node.Shape.App(fn, arg) => {
//        println("App!")
//        println((fn, arg))
//      }
//    }
  }
}
