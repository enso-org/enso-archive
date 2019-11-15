package org.enso.graph

import org.enso.graph.definition.Macro.component

/**
  * Example graph components for use in testing.
  */
object GraphComponents {

  // === Node ===
  @component case class Nodes() { type Node[G <: Graph] }

  // === Edge ===
  @component case class Edges() { type Edge[G <: Graph] }

}
