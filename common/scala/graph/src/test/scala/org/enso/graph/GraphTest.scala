package org.enso.graph

import org.enso.graph.Graph.{Component, _}
import org.enso.graph.GraphComponents.{Edge, Edges, Node, Nodes}
import org.enso.graph.definition.Macro.field
import org.scalatest.{FlatSpec, Matchers}
import shapeless.nat._
import shapeless.{::, HNil}

class GraphTest extends FlatSpec with Matchers {
  object Impl {


    // ==========================================================================
    // === Component definitions ================================================
    // ==========================================================================

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // Please note, that the following definitions are very error prone to both
    // read and write and should be refactored using macros.
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

    object Node {
      // ==================
      // === Node Shape ===
      // ==================

      // provide a @variantField that takes a trait and some case classes
      sealed trait Shape      extends Graph.Component.Field
      final case class Null() extends Shape
      final case class App()  extends Shape
      object Shape {
        // It has to include max of all fields + 1. The first field encodes constructor ID
        implicit def sized = new Sized[Shape] { type Out = _4 }
      }

      object Null {
        val any            = Component.VariantMatcher[Shape, Null](0)
        implicit def sized = new Sized[Null] { type Out = _0 }
      }

      object App {
        implicit def sized = new Sized[App] { type Out = _1 }

        val any = Component.VariantMatcher[Shape, App](1)
        def unapply[G <: Graph, C <: Component](arg: Component.Ref[G, C])(
          implicit
          graph: GraphData[G],
          ev: HasComponentField[G, C, Shape]
        ): Option[(Edge[G], Edge[G])] =
          any.unapply(arg).map(t => (Component.Ref(t.fn), Component.Ref(t.arg)))

        implicit class Instance[G <: Graph, C <: Component](
          node: Component.Refined[Shape, App, Component.Ref[G, C]]
        ) {

          // TODO [AA] This boilerplate should be macro'd away
          def fn(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, Shape]
          ): Int = {
            graph
              .unsafeReadField[C, Shape](Component.Refined.unwrap(node).ix, 1)
          }

          def fn_=(value: Int)(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, Shape]
          ): Unit = {
            graph.unsafeWriteField[C, Shape](
              Component.Refined.unwrap(node).ix,
              1,
              value
            )
          }

          def arg(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, Shape]
          ): Int = {
            graph
              .unsafeReadField[C, Shape](Component.Refined.unwrap(node).ix, 2)
          }

          def arg_=(value: Int)(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, Shape]
          ): Unit = {
            graph.unsafeWriteField[C, Shape](
              Component.Refined.unwrap(node).ix,
              2,
              value
            )
          }
        }
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
    import Impl.Edge.Shape.implicits._
    import Impl.Node.ParentLink.implicits._
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
//      case Impl.Node.Null.any(n @ _) => {
//        println("Null!")
//      }
//      case Impl.Node.App.any(app) => {
//        println("App!")
//        println(app.fn)
//        println(app.parent)
//      }
//      case Impl.Node.App(fn, arg) => {
//        println("App!")
//        println((fn, arg))
//      }
//    }
  }
}
