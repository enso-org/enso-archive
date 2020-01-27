package org.enso.core

import org.enso.graph.definition.Macro.component
import org.enso.graph.{Graph => PrimGraph}
import shapeless.{::, HNil}

object CoreDefinition {

  // ==========================================================================
  // === The Graph Marker Trait ===============================================
  // ==========================================================================

  case class CorePrim() extends PrimGraph

  implicit def components =
    new PrimGraph.Component.List[CorePrim] {
      type Out = Nodes :: Links :: HNil
    }

  implicit def nodeFields =
    new PrimGraph.Component.Field.List[CorePrim, Nodes] {
      type Out = HNil
    }

  implicit def linkFields =
    new PrimGraph.Component.Field.List[CorePrim, Links] {
      type Out = HNil
    }

  // ==========================================================================
  // === Graph Components =====================================================
  // ==========================================================================

  @component case class Nodes() { type Node[G <: PrimGraph] }
  @component case class Links() { type Link[G <: PrimGraph] }

}
