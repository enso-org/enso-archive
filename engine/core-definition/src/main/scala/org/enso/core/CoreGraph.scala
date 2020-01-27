package org.enso.core

import org.enso.graph.definition.Macro.{component, field}
import org.enso.graph.{Graph => PrimGraph, Sized}
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

  /** The list of fields that a [[Node]] has in a [[CoreGraph]]. */
  implicit def nodeFields =
    new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
      type Out = HNil
    }

  /** The list of fields that a [[Link]] has in a [[CoreGraph]]. */
  implicit def linkFields =
    new PrimGraph.Component.Field.List[CoreGraph, Links] {
      type Out = HNil
    }

  // ==========================================================================
  // === Node =================================================================
  // ==========================================================================

  /** A node in the [[CoreGraph]]. */
  @component case class Nodes() { type Node[G <: PrimGraph] }

  /** A link between nodes in the [[CoreGraph]]. */
  @component case class Links() { type Link[G <: PrimGraph] }

  object Node {}

  // ==========================================================================
  // === Link =================================================================
  // ==========================================================================

  object Link {}
}
