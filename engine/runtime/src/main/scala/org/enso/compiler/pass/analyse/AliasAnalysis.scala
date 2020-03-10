package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Expression
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Edge, Scope}

case object AliasAnalysis extends IRPass {

  override type Metadata = Info

  override def runModule(ir: IR.Module): IR.Module = {
    val bindings = ir.bindings

    val newBindings = bindings.map {
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        val graph = new Graph
        m.copy(body = runInScope(graph, graph.scopes, body, true))
          .addMetadata(Info(graph, -1))
      case a @ IR.Module.Scope.Definition.Atom(_, _, _, _) => ???
    }

    ir.copy(bindings = newBindings)
  }

  def runInScope(
    graph: Graph,
    parentScope: Scope,
    ir: IR.Expression,
    reuseScope: Boolean = false
  ): IR.Expression = {
    ir match {
      case lambda @ IR.Function.Lambda(arguments, body, _, _, _) =>
        val newScope =
          if (reuseScope) parentScope
          else {
            val newScope = new Scope(Some(parentScope))
            parentScope.children ::= newScope
            newScope
          }
        arguments.foreach {
          case IR.DefinitionArgument.Specified(name, _, _, _, _) =>
            newScope.nodes += Graph.Name.Def(graph.nextId, name.name)
        }
        lambda.copy(body = runInScope(graph, newScope, body, true))

      case block @ IR.Expression.Block(expressions, returnValue, _, _, _) =>
        val newScope =
          if (reuseScope) parentScope
          else {
            val newScope = new Scope(Some(parentScope))
            parentScope.children ::= newScope
            newScope
          }
        block.copy(
          expressions = expressions.map(runInScope(graph, newScope, _)),
          returnValue = runInScope(graph, newScope, returnValue)
        )

      case assignment @ IR.Expression.Binding(name, expression, _, _) =>
        parentScope.nodes += Graph.Name.Def(graph.nextId, name.name)
        assignment.copy(expression = runInScope(graph, parentScope, expression))

      case ir @ IR.Name.Literal(n, _, _) =>
        val id   = graph.nextId
        val node = Graph.Name.Use(id, n)
        parentScope.nodes += node
        resolve(node, parentScope).foreach(graph.edges += _)
        ir.addMetadata(Info(graph, id))

      case x => x.mapExpressions(runInScope(graph, parentScope, _))
    }
  }

  def resolve(
    node: Graph.Name.Use,
    scope: Scope,
    parentCounter: Int = 0
  ): Option[Graph.Edge] = {
    val definition = scope.nodes.find {
      case Graph.Name.Def(_, n) => n == node.name
      case _                    => false
    }

    definition match {
      case None         => scope.parent.flatMap(resolve(node, _, parentCounter + 1))
      case Some(target) => Some(Graph.Edge(node.id, parentCounter, target.id))
    }
  }

  override def runExpression(ir: IR.Expression): IR.Expression = ir

  // === Data Definitions =====================================================

  sealed case class Info(graph: Graph, id: Graph.Id) extends IR.Metadata

  sealed class Graph {
    var edges: Set[Graph.Edge] = Set()
    private var nextIdCounter  = 0

    def nextId: Graph.Id = {
      val nextId = nextIdCounter
      nextIdCounter += 1
      nextId
    }

    var scopes: Graph.Scope = new Graph.Scope

    override def toString: String = s"Graph($edges, $scopes)"
  }
  object Graph {
    type Id = Int

    sealed class Scope(val parent: Option[Scope] = None) {
      var children: List[Scope] = List()

      var nodes: Set[Name] = Set()

      override def toString: String = s"Scope($children, $nodes)"
    }

    sealed case class Edge(source: Id, scopeCount: Int, target: Id)

    sealed trait Name { val id: Id }
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
