package org.enso.core

import org.enso.graph.definition.Macro.{component, field, opaque}
import org.enso.graph.{Sized, Graph => PrimGraph}
import shapeless.{::, HNil}

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
  @opaque case class StringLiteral(opaque: String)

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
      type Out = Node.ParentLinks :: Node.Location :: HNil
    }

  object Node {
    @field case class Location[G <: PrimGraph](sourceStart: Int, sourceEnd: Int)

    @field case class ParentLinks[G <: PrimGraph](
      parents: OpaqueData[Vector[Int], ParentStorage]
    )
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
    @field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])
  }
}
