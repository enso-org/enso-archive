package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass

case object AliasAnalysis extends IRPass {

  override type Metadata = Info

  override def runModule(ir: IR.Module): IR.Module = {
    val bindings = ir.bindings

    bindings.foreach(println)

    ir
  }

  override def runExpression(ir: IR.Expression): IR.Expression = ir

  // === Data Definitions =====================================================

  sealed case class Info(graph: Graph, id: Graph.Id) extends IR.Metadata

  sealed class Graph {
    val edges: Set[Graph.Edge] = Set()
    val scopes: Graph.Scope    = Graph.Scope()
  }
  object Graph {
    type Id = Int

    sealed case class Scope(
      parent: Option[Scope] = None,
      children: List[Scope] = List(),
      nodes: Set[Name]      = Set()
    )

    sealed case class Edge(source: Id, scopeCount: Int, target: Id)

    sealed trait Name
    object Name {
      sealed case class Def(id: Id, name: String) extends Name
      sealed case class Use(id: Id, name: String) extends Name
    }
  }

  // Flat, but with subgraphs
  // Root, Root node, usages, defns

  /* - Codegen (traverse the tree of scopes)
 * - Need to know incoming and outgoing edges
 */
}
