package org.enso.graph

import org.enso.graph.{Graph => PrimGraph}
import org.scalatest.{FlatSpec, Matchers}
import shapeless.nat._
import shapeless.{::, HNil}

class GraphTest extends FlatSpec with Matchers {
  object GraphImpl {

    // ========================================================================
    // === Example Graph Implementation =======================================
    // ========================================================================

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

    // ========================================================================
    // === Component Definitions ==============================================
    // ========================================================================

    // === Node ===
//    @component case class Nodes() { type Node[G <: PrimGraph] }
    sealed case class Nodes() extends PrimGraph.Component
    type Node[G <: PrimGraph] = PrimGraph.Component.Ref[G, Nodes]
    implicit class GraphWithNodes[G <: PrimGraph](
      graph: PrimGraph.GraphData[G]
    ) {
      def addNode()(implicit ev: PrimGraph.HasComponent[G, Nodes]): Node[G] = {
        graph.addComponent[Nodes]()
      }
    }

    // === Edge ===
//    @component case class Edges() { type Edge[G <: PrimGraph] }
    sealed case class Edges() extends PrimGraph.Component
    type Edge[G <: PrimGraph] = PrimGraph.Component.Ref[G, Edges]
    implicit class GraphWithEdges[G <: PrimGraph](
      graph: PrimGraph.GraphData[G]
    ) {
      def addEdge()(implicit ev: PrimGraph.HasComponent[G, Edges]): Edge[G] = {
        graph.addComponent[Edges]()
      }
    }

    // ========================================================================
    // === Component Field Definitions ========================================
    // ========================================================================

    object Node {

      // === Node Shape ===
//      @field object Shape {
//        type G = PrimGraph
//        case class Null()
//        case class App(fn: Edge[G], argTest: Edge[G])
//      }

      sealed trait Shape extends PrimGraph.Component.Field
      object Shape {
        implicit def sized = new Sized[Shape] { type Out = _3 }

        sealed case class Null() extends Shape;
        object Null {
          val any = PrimGraph.Component.VariantMatcher[Shape, Null](0)

          implicit def sized = new Sized[Null] { type Out = _0 }
        }

        sealed case class App() extends Shape
        object App {
          val any            = PrimGraph.Component.VariantMatcher[Shape, App](1)
          implicit def sized = new Sized[App] { type Out = _2 }

          def unapply[G <: PrimGraph, C <: PrimGraph.Component](
            arg: PrimGraph.Component.Ref[G, C]
          )(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Option[(Edge[G], Edge[G])] =
            any.unapply(arg).map(((t) => scala.Tuple2(t.fn, t.arg)))

          implicit class AppInstance[G <: PrimGraph, C <: PrimGraph.Component](
            node: PrimGraph.Component.Refined[
              Shape,
              App,
              PrimGraph.Component.Ref[G, C]
            ]
          ) {
            def fn(
              implicit graph: PrimGraph.GraphData[G],
              ev: PrimGraph.HasComponentField[G, C, Shape]
            ): Edge[G] =
              PrimGraph.Component.Ref(
                graph.unsafeReadField[C, Shape](
                  PrimGraph.Component.Refined.unwrap(node).ix,
                  0
                )
              )

            def fn_=(value: Edge[G])(
              implicit graph: PrimGraph.GraphData[G],
              ev: PrimGraph.HasComponentField[G, C, Shape]
            ): Unit =
              graph.unsafeWriteField[C, Shape](
                PrimGraph.Component.Refined.unwrap(node).ix,
                0,
                value.ix
              )

            def arg(
              implicit graph: PrimGraph.GraphData[G],
              ev: PrimGraph.HasComponentField[G, C, Shape]
            ): Edge[G] =
              PrimGraph.Component.Ref(
                graph.unsafeReadField[C, Shape](
                  PrimGraph.Component.Refined.unwrap(node).ix,
                  1
                )
              )

            def arg_=(value: Edge[G])(
              implicit graph: PrimGraph.GraphData[G],
              ev: PrimGraph.HasComponentField[G, C, Shape]
            ): Unit =
              graph.unsafeWriteField[C, Shape](
                PrimGraph.Component.Refined.unwrap(node).ix,
                1,
                value.ix
              )
          }
        }
      }

      sealed case class ParentLink() extends PrimGraph.Component.Field
      object ParentLink {
        implicit def sized = new Sized[ParentLink] { type Out = _1 }

        implicit class ParentLinkInstance[
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          node: PrimGraph.Component.Ref[G, C]
        ) {
          def parent(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, ParentLink]
          ): Edge[G] = {
            PrimGraph.Component.Ref(
              graph.unsafeReadField[C, ParentLink](node.ix, 0)
            )
          }

          def parent_=(value: Edge[G])(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, ParentLink]
          ): Unit = {
            graph.unsafeWriteField[C, ParentLink](node.ix, 0, value.ix)
          }
        }

        implicit def ParentLink_transInstance[
          F <: PrimGraph.Component.Field,
          R,
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          t: PrimGraph.Component.Refined[F, R, PrimGraph.Component.Ref[G, C]]
        ): ParentLinkInstance[G, C] = t.wrapped
      }
    }

    object Edge {

      sealed case class Shape() extends PrimGraph.Component.Field;
      object Shape {
        implicit def sized = new Sized[Shape] { type Out = _2 }

        implicit class ShapeInstance[G <: PrimGraph, C <: PrimGraph.Component](
          node: PrimGraph.Component.Ref[G, C]
        ) {
          def source(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Node[G] =
            PrimGraph.Component.Ref(
              graph.unsafeReadField[C, Shape](node.ix, 0)
            )

          def source_=(value: Node[G])(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Unit = graph.unsafeWriteField[C, Shape](node.ix, 0, value.ix)

          def target(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Node[G] =
            PrimGraph.Component.Ref(
              graph.unsafeReadField[C, Shape](node.ix, 1)
            )

          def target_=(value: Node[G])(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Unit = graph.unsafeWriteField[C, Shape](node.ix, 1, value.ix)
        }

        implicit def Shape_transInstance[
          F <: PrimGraph.Component.Field,
          R,
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          t: PrimGraph.Component.Refined[F, R, PrimGraph.Component.Ref[G, C]]
        ): ShapeInstance[G, C] = t.wrapped
      };
    }
  }

  // ==========================================================================
  // === Example Graph Usage ==================================================
  // ==========================================================================

  import GraphImpl._
  import GraphImpl.Node.Shape._
  import GraphImpl.Node.ParentLink._
  import GraphImpl.Edge.Shape._

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
