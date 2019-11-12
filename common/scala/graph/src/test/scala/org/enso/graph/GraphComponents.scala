package org.enso.graph

import org.enso.graph.Graph.{Component, GraphData, HasComponent}

/**
  * Example graph components for use in testing.
  */
object GraphComponents {
  // === Node ===

  // @component macro that takes a case class to define these
  final case class Nodes() extends Component
  type Node[G <: Graph] = Component.Ref[G, Nodes]
  implicit class GraphWithNodes[G <: Graph](graph: GraphData[G]) {
    def addNode()(implicit ev: HasComponent[G, Nodes]): Node[G] = {
      graph.addComponent[Nodes]()
    }
  }

  // === Edge ===

  final case class Edges() extends Component
  type Edge[G <: Graph] = Component.Ref[G, Edges]
  implicit class GraphWithEdges[G <: Graph](graph: GraphData[G]) {
    def addEdge()(implicit ev: HasComponent[G, Edges]): Edge[G] = {
      graph.addComponent[Edges]()
    }
  }
}
