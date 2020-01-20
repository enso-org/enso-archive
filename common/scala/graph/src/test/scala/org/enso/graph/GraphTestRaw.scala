package org.enso.graph

import org.enso.graph.{Graph => PrimGraph}
import org.scalatest.{FlatSpec, Matchers}
import shapeless.nat._
import shapeless.{::, HNil}

import scala.collection.mutable

// TODO [AA] Can I macro this into a separate file like AST does?
class GraphTestRaw extends FlatSpec with Matchers {
  object GraphImpl {

    // ========================================================================
    // === Example Graph Implementation =======================================
    // ========================================================================

    case class Graph() extends PrimGraph

    implicit def components[G <: PrimGraph] =
      new PrimGraph.Component.List[Graph] {
        type Out = Nodes :: Edges :: HNil
      }

    implicit def nodeFields[G <: PrimGraph] =
      new PrimGraph.Component.Field.List[Graph, Nodes] {
        type Out = Node.Shape :: Node.ParentLink :: Node.Location :: HNil
      }

    implicit def edgeFields[G <: PrimGraph] =
      new PrimGraph.Component.Field.List[Graph, Edges] {
        type Out = Edge.Shape :: HNil
      }

    // ========================================================================
    // === Component Definitions ==============================================
    // ========================================================================

    // === Node ===
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

      sealed trait Shape extends PrimGraph.Component.Field
      object Shape {
        implicit def sized = new Sized[Shape] { type Out = _3 }

        sealed case class Null() extends Shape
        object Null {
          val index = 0
          val any   = PrimGraph.Component.VariantMatcher[Shape, Null](0)

          implicit def sized = new Sized[Null] { type Out = _0 }
        }

        sealed case class App() extends Shape
        object App {
          val index          = 1
          val any            = PrimGraph.Component.VariantMatcher[Shape, App](1)
          implicit def sized = new Sized[App] { type Out = _2 }

          def unapply[G <: PrimGraph, C <: PrimGraph.Component](
            arg: PrimGraph.Component.Ref[G, C]
          )(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Option[(Edge[G], Edge[G])] =
            any.unapply(arg).map(t => scala.Tuple2(t.fn, t.arg))

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

        // A centre section
        sealed case class Centre() extends Shape
        object Centre {
          val index          = 2
          val any            = PrimGraph.Component.VariantMatcher[Shape, App](2)
          implicit def sized = new Sized[Centre] { type Out = _1 }

          def unapply[G <: PrimGraph, C <: PrimGraph.Component](
            arg: PrimGraph.Component.Ref[G, C]
          )(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Option[scala.Tuple1[Edge[G]]] =
            any.unapply(arg).map(t => scala.Tuple1(t.fn))

          implicit class CentreInstance[
            G <: PrimGraph,
            C <: PrimGraph.Component
          ](
            node: PrimGraph.Component.Refined[
              Shape,
              Centre,
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
          }
        }
      }

      // TODO [AA] Can do this with fields
      // TODO [AA] Can generate named accessor for the base type
      // TODO [AA] Will need to copy generic tparams around
      sealed case class ParentLink() extends PrimGraph.Component.Field
      object ParentLink {
        implicit def sized[G <: PrimGraph] =
          new Sized[ParentLink] { type Out = _1 }

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

      sealed case class Location(line: Int, column: Int)
          extends PrimGraph.Component.Field;
      object Location {
        implicit def sized = new Sized[Location] { type Out = _2 }

        implicit class LocationInstance[
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          node: PrimGraph.Component.Ref[G, C]
        ) {
          def line(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Int =
            graph.unsafeReadField[C, Location](node.ix, 0)

          def line_=(value: Int)(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Unit = graph.unsafeWriteField[C, Location](node.ix, 0, value)

          def column(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Int =
            graph.unsafeReadField[C, Location](node.ix, 1)

          def column_=(value: Int)(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Unit = graph.unsafeWriteField[C, Location](node.ix, 1, value)

          def location(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Location =
            Location(this.line, this.column)

          def location_=(value: Location)(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Location]
          ): Unit = {
            this.line   = value.line
            this.column = value.column
          }
        }

        implicit def Location_transInstance[
          F <: PrimGraph.Component.Field,
          R,
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          t: PrimGraph.Component.Refined[F, R, PrimGraph.Component.Ref[G, C]]
        ): LocationInstance[G, C] = t.wrapped
      };

      // TODO [AA] Variants should be able to support nested types.
      // TODO [AA] How to macro this -> Opaque[T, M], where M is the name of the map type
      sealed case class NameMap(str: mutable.Map[Int, String])
      sealed case class Name(str: String) extends PrimGraph.Component.Field;
      object Name {
        implicit def sized = new Sized[Name] { type Out = _0 }

        implicit class NameInstance[
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          node: PrimGraph.Component.Ref[G, C]
        ) {
          def name(
            implicit graph: PrimGraph.GraphData[G],
            map: NameMap,
            ev: PrimGraph.HasComponentField[G, C, Name]
          ): String =
            map.str(node.ix)

          def name_=(value: String)(
            implicit graph: PrimGraph.GraphData[G],
            map: NameMap,
            ev: PrimGraph.HasComponentField[G, C, Name]
          ): Unit =
            map.str(node.ix) = value
        }

        implicit def Name_transInstance[
          F <: PrimGraph.Component.Field,
          R,
          G <: PrimGraph,
          C <: PrimGraph.Component
        ](
          t: PrimGraph.Component.Refined[F, R, PrimGraph.Component.Ref[G, C]]
        ): NameInstance[G, C] = t.wrapped
      };
    }

    object Edge {

      // TODO [AA] Can I add tparams safely here?
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
  import GraphImpl.Node.Location._
  import GraphImpl.Node.Name._
  import GraphImpl.Edge.Shape._

  implicit val graph: PrimGraph.GraphData[GraphImpl.Graph] =
    PrimGraph[GraphImpl.Graph]();

  implicit val nameMap: Node.NameMap = Node.NameMap(mutable.Map())

  val n1: Node[Graph] = graph.addNode()
  val n2: Node[Graph] = graph.addNode()
  val n3: Node[Graph] = graph.addNode()

  val e1: Edge[Graph] = graph.addEdge()
  e1.source = n1
  e1.target = n2

  println(n1.parent)
  n1.parent = e1

  println(n2.parent)
  n2.parent = e1

  // The size calculation can't match `ParentLink[G]` against `ParentLink[G]`
  case class Foo[G <: PrimGraph]() extends PrimGraph.Component.Field

  def foo[G <: PrimGraph, G1 <: PrimGraph]: Unit =
    implicitly[Foo[G] =:= Foo[G1]]

  println(n3.parent)
  n3.parent = e1

  n1.name = "foo"

  // This is just dirty and very unsafe way of changing `n1` to be App!
  graph.unsafeWriteField[Nodes, GraphImpl.Node.Shape](n1.ix, 0, 1)

  // ==========================================================================
  // === Tests ================================================================
  // ==========================================================================

  "Nested variants" should "work without issue" in {
    val nestedNode: Node[Graph] = graph.addNode()

    // This changes the shape of `nestedNode` to be `Centre`
    graph.unsafeWriteField[Nodes, GraphImpl.Node.Shape](
      nestedNode.ix,
      0,
      GraphImpl.Node.Shape.Centre.index
    )

    // TODO [AA] Make this work
  }

  "Component fields" should "be able to be accessed by their types" in {
    n1.line   = 10
    n1.column = 5

    n1.line shouldEqual 10
    n1.column shouldEqual 5

    n1.location = Node.Location(1, 2)

    n1.line shouldEqual 1
    n1.column shouldEqual 2
  }

  "Opdaque types" should "be accessed successfully" in {
    val nameStr = "TestName"
    n1.name = nameStr

    n1.name shouldEqual nameStr
  }

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
