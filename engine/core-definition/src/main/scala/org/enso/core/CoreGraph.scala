package org.enso.core

import org.enso.graph.definition.Macro.{component, field, opaque, OpaqueData}
import org.enso.graph.{Sized, Graph => PrimGraph, VariantIndexed}
import shapeless.{::, HNil}
import shapeless.nat._

object CoreGraph {

  // ==========================================================================
  // === The Graph Definition =================================================
  // ==========================================================================

  /** This type denotes the core graph itself. */
  case class CoreGraph() extends PrimGraph

  /** The list of components that make up a [[CoreGraph]]. */
  implicit def components =
    new PrimGraph.Component.List[CoreGraph] {
      type Out = Nodes :: Links :: HNil
    }

  // ==========================================================================
  // === Opaque Storage =======================================================
  // ==========================================================================

  /** Storage for string literals  */
  @opaque case class Literal(opaque: String)

  /** Storage for parents for a given node.
    *
    * An entry in the vector will be the index of an [[Edge]] in the graph that
    * has the containing node as its `target` field.
    */
  @opaque case class Parent(opaque: Vector[Int])

  // ==========================================================================
  // === Node =================================================================
  // ==========================================================================

  /** A node in the [[CoreGraph]]. */
  @component case class Nodes() { type Node[G <: PrimGraph] }

  /** The list of fields that a [[Node]] has in a [[CoreGraph]]. */
  implicit def nodeFields =
    new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
      type Out = Node.Shape :: Node.ParentLinks :: Node.Location :: HNil
    }

  object Node {

    /** A location describes which portion of the source code this particular
      * node in the graph represents.
      *
      * @param sourceStart the start position in the source code
      * @param sourceEnd the end position in the source code
      * @tparam G the graph type
      */
    @field case class Location[G <: PrimGraph](sourceStart: Int, sourceEnd: Int)

    /** This type represents all the incoming [[Link]]s to the current node.
      *
      * It should be noted that it _does not_ store the links directly. This
      * would only make sense if the link direction was reversed. Instead, it
      * holds unsafe references to the incoming link in the underlying graph.
      * These can be turned into the [[Link]]s directly by using
      * [[PrimGraph.GraphData.componentReferenceFromIndex()]].
      *
      * @param parents a vector containing the raw indices of the parent links
      * @tparam G the graph type
      */
    @field case class ParentLinks[G <: PrimGraph](
      parents: OpaqueData[Vector[Int], ParentStorage]
    )

    /** The shape of a node represents all the different forms that a node can
      * take.
      */
    @field object Shape {
      type G = PrimGraph

      // === Base Shapes ======================================================
      case class Empty()
      case class Cons(head: Link[G], tail: Link[G])
      case class Nil()

      // === Literals =========================================================
      case class RawLiteral(literal: OpaqueData[String, LiteralStorage])
      case class NumericLiteral(number: Link[G])
    }
  }

  // ==========================================================================
  // === Link =================================================================
  // ==========================================================================

  /** A link between nodes in the [[CoreGraph]]. */
  @component case class Links() { type Link[G <: PrimGraph] }

  /** The list of fields that a [[Link]] has in a [[CoreGraph]]. */
  implicit def linkFields =
    new PrimGraph.Component.Field.List[CoreGraph, Links] {
      type Out = Link.Shape :: HNil
    }

  object Link {

    /** The shape of a link is static and represents a standard directional edge
      * in a graph.
      *
      * @param source the node at the start of the link
      * @param target the node at the end of the link
      * @tparam G the graph type
      */
    @field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])
  }
}
