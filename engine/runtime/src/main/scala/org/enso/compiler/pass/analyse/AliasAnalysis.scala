package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.Scope

case object AliasAnalysis extends IRPass {

  val invalidID: Graph.Id = -1

  override type Metadata = Info

  override def runModule(ir: IR.Module): IR.Module = {
    ir.copy(bindings = ir.bindings.map(analyseModuleDefinition))
  }

  override def runExpression(ir: IR.Expression): IR.Expression = ir

  def analyseModuleDefinition(
    ir: IR.Module.Scope.Definition
  ): IR.Module.Scope.Definition = {
    val topLevelGraph = new Graph

    ir match {
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        m.copy(
            body = analyseExpression(
              body,
              topLevelGraph,
              topLevelGraph.rootScope,
              reuseScope = true
            )
          )
          .addMetadata(Info.RootScope(topLevelGraph))
      case a @ IR.Module.Scope.Definition.Atom(_, args, _, _) =>
        a.copy(
            arguments = analyseArgumentDefs(
              args,
              topLevelGraph,
              topLevelGraph.rootScope,
              reuseScope = true
            )
          )
          .addMetadata(Info.RootScope(topLevelGraph))
    }
  }

  def analyseArgumentDefs(
    args: List[IR.DefinitionArgument],
    graph: Graph,
    parentScope: Scope,
    reuseScope: Boolean = true
  ): List[IR.DefinitionArgument] = {
    args.map {
      case arg @ IR.DefinitionArgument.Specified(name, value, _, _, _) =>
        val id = graph.nextId
        parentScope.names += Graph.Name.Def(id, name.name)

        arg
          .copy(
            defaultValue = value
              .map(
                (ir: IR.Expression) =>
                  analyseExpression(ir, graph, parentScope, reuseScope)
              )
          )
          .addMetadata(Info.Child(graph, id))
    }
  }

  // TODO [AA] Re-write from analyseExpression, get head around scope reuse
  //  new scopes should probably be injected rather than constructed inside
  def analyseExpression(
    ir: IR.Expression,
    graph: Graph,
    parentScope: Scope,
    reuseScope: Boolean = true
  ): IR.Expression = {
    val currentScope =
      if (reuseScope) {
        parentScope
      } else {
        val newScope = new Scope(Some(parentScope))
        parentScope.childScopes ::= newScope
        newScope
      }

    ir match {
      // TODO [AA] Should function arguments be in the same scope as the body?
      case lambda @ IR.Function.Lambda(arguments, body, _, _, _) =>
        lambda.copy(
          body      = analyseExpression(body, graph, currentScope),
          arguments = analyseArgumentDefs(arguments, graph, currentScope)
        )

      case block @ IR.Expression.Block(expressions, returnValue, _, _, _) =>
        block.copy(
          expressions = expressions.map(
            (ir: IR.Expression) => analyseExpression(ir, graph, currentScope)
          ),
          returnValue = analyseExpression(
            returnValue,
            graph,
            currentScope,
            reuseScope = false
          )
        )

      case assignment @ IR.Expression.Binding(name, expression, _, _) =>
        parentScope.names += Graph.Name.Def(graph.nextId, name.name)
        assignment.copy(
          expression = analyseExpression(expression, graph, parentScope)
        )

      case ir @ IR.Name.Literal(n, _, _) =>
        val id   = graph.nextId
        val node = Graph.Name.Use(id, n)
        parentScope.names += node
        resolveUsages(node, parentScope).foreach(graph.links += _)
        ir.addMetadata(Info.Child(graph, id))

      // TODO [AA] This cannot
      case x =>
        x.mapExpressions(
          (ir: IR.Expression) => analyseExpression(ir, graph, parentScope, reuseScope = false)
        )
    }
  }

  def resolveUsages(
    node: Graph.Name.Use,
    scope: Scope,
    parentCounter: Int = 0
  ): Option[Graph.Link] = {
    val definition = scope.names.find {
      case Graph.Name.Def(_, n) => n == node.name
      case _                    => false
    }

    definition match {
      case None =>
        scope.parent.flatMap(resolveUsages(node, _, parentCounter + 1))
      case Some(target) => Some(Graph.Link(node.id, parentCounter, target.id))
    }
  }

  // === Data Definitions =====================================================

  sealed trait Info extends IR.Metadata
  object Info {
    sealed case class RootScope(graph: Graph)           extends Info
    sealed case class Child(graph: Graph, id: Graph.Id) extends Info
  }

  sealed class Graph {
    var links: Set[Graph.Link] = Set()
    var rootScope: Graph.Scope = new Graph.Scope

    private var nextIdCounter = 0

    def nextId: Graph.Id = {
      val nextId = nextIdCounter
      nextIdCounter += 1
      nextId
    }

    override def toString: String =
      s"Graph(links = $links, rootScope = $rootScope)"
  }
  object Graph {
    type Id = Int

    sealed class Scope(val parent: Option[Scope] = None) {
      var childScopes: List[Scope] = List()

      var names: Set[Name] = Set()

      override def toString: String =
        s"Scope(childScopes = $childScopes, names = $names)"
    }

    sealed case class Link(source: Id, scopeCount: Int, target: Id)

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
