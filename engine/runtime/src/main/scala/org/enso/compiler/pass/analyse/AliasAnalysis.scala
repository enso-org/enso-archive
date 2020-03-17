package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Occurrence, Scope}
import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}

import scala.reflect.ClassTag

/** This pass performs scope identification and analysis, as well as symbol
  * resolution where it is possible to do so statically.
  */
case object AliasAnalysis extends IRPass {

  /** Alias information for the IR. */
  override type Metadata = Info

  /** Performs alias analysis on a module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(ir: IR.Module): IR.Module = {
    ir.copy(bindings = ir.bindings.map(analyseModuleDefinition))
  }

  /** Performs alias analysis on an inline expression, starting from the
    * provided scope.
    *
    * @param ir the Enso IR to process
    * @param localScope the local scope in which the expression is executed
    * @param moduleScope the module scope in which the expression is executed
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  // TODO [AA] What is a sensible way to do this?
  override def runExpression(
    ir: IR.Expression,
    localScope: Option[LocalScope]   = None,
    moduleScope: Option[ModuleScope] = None
  ): IR.Expression = ir

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
              topLevelGraph.rootScope
            )
          )
          .addMetadata(Info.RootScope(topLevelGraph))
    }
  }

  def analyseArgumentDefs(
    args: List[IR.DefinitionArgument],
    graph: Graph,
    parentScope: Scope
  ): List[IR.DefinitionArgument] = {
    args.map {
      case arg @ IR.DefinitionArgument.Specified(name, value, _, _, _) =>
        val id = graph.nextId()
        parentScope.addOccurrence(Graph.Occurrence.Def(id, name.name))

        arg
          .copy(
            defaultValue = value
              .map(
                (ir: IR.Expression) =>
                  analyseExpression(ir, graph, parentScope, reuseScope = true)
              )
          )
          .addMetadata(Info.Child(graph, id))
    }
  }

  // TODO [AA] Don't know how to get the scoping right....
  def analyseExpression(
    expression: IR.Expression,
    graph: Graph,
    parentScope: Scope,
    reuseScope: Boolean = false
  ): IR.Expression = {
    val currentScope = if (reuseScope) parentScope else parentScope.addChild()

    expression match {
      case block @ IR.Expression.Block(_, _, _, _, _) => block
      case IR.Expression.Binding(_, _, _, _)          => expression
      case x =>
        x.mapExpressions(
          analyseExpression(_, graph, currentScope, reuseScope = true)
        )
    }
  }

  // TODO [AA] Re-write from analyseExpression, get head around scope reuse
  //  new scopes should probably be injected rather than constructed inside
  def analyseExpression2(
    ir: IR.Expression,
    graph: Graph,
    parentScope: Scope,
    reuseScope: Boolean = false
  ): IR.Expression = {
    val currentScope = if (reuseScope) parentScope else parentScope.addChild()

    ir match {
      // TODO [AA] Should function arguments be in the same scope as the body?
      case lambda @ IR.Function.Lambda(arguments, body, _, _, _) =>
        lambda.copy(
          body      = analyseExpression(body, graph, currentScope, reuseScope = true),
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
            currentScope
          )
        )

      case assignment @ IR.Expression.Binding(name, expression, _, _) =>
        currentScope.addOccurrence(
          Graph.Occurrence.Def(graph.nextId(), name.name)
        )
        assignment.copy(
          expression = analyseExpression(expression, graph, currentScope)
        )

      case ir @ IR.Name.Literal(n, _, _) =>
        val id   = graph.nextId()
        val node = Graph.Occurrence.Use(id, n)
        currentScope.addOccurrence(node)
        graph.resolveUsage(node, currentScope).foreach(graph.links += _)
        ir.addMetadata(Info.Child(graph, id))

      // TODO [AA] This cannot
      case x =>
        x.mapExpressions(
          (ir: IR.Expression) => analyseExpression(ir, graph, currentScope)
        )
    }
  }

  // === Data Definitions =====================================================

  /** Information about the aliasing state for a given IR node. */
  sealed trait Info extends IR.Metadata
  object Info {

    /** Aliasing information for a root scope.
      *
      * A root scope has a 1:1 correspondence with a
      * [[org.enso.interpreter.node.EnsoRootNode]].
      *
      * @param graph the graph containing the alias information for that node
      */
    sealed case class RootScope(graph: Graph) extends Info

    /** Aliasing information for a piece of [[IR]] that is contained within a
      * [[RootScope]].
      *
      * @param graph the graph in which this IR node can be found
      * @param id the identifier of this IR node in `graph`
      */
    sealed case class Child(graph: Graph, id: Graph.Id) extends Info
  }

  /** A graph containing aliasing information for a given root scope in Enso. */
  sealed class Graph {
    var links: Set[Graph.Link] = Set()
    var rootScope: Graph.Scope = new Graph.Scope()

    private var nextIdCounter = 0

    /** Generates a new identifier for a node in the graph.
      *
      * @return a unique identifier for this graph
      */
    def nextId(): Graph.Id = {
      val nextId = nextIdCounter
      nextIdCounter += 1
      nextId
    }

    /** Resolves any links for the given usage of a symbol.
      *
      * @param occurrence the symbol usage
      * @param scope the scope in which the symbol is used
      * @return the link, if it exists
      */
    def resolveUsage(
      occurrence: Graph.Occurrence.Use,
      scope: Scope
    ): Option[Graph.Link] = {
      scope
        .resolveUsage(occurrence)
        .flatMap(link => {
          links += link
          Some(link)
        })
    }

    /** Returns a string representation of the graph.
      *
      * @return a string representation of `this`
      */
    override def toString: String =
      s"Graph(links = $links, rootScope = $rootScope)"

    /** Gets all links in which the provided `id` is a participant.
      *
      * @param id the identifier for the symbol
      * @return a list of links in which `id` occurs
      */
    def linksForId(id: Graph.Id): Set[Graph.Link] = {
      links.filter(l => l.source == id || l.target == id)
    }

    /** Gets the scope where a given ID is defined in the graph.
      *
      * @param id the id to find the scope for
      * @return the scope where `id` occurs
      */
    def scopeForId(id: Graph.Id): Option[Graph.Scope] = {
      rootScope.scopeForId(id)
    }

    /** Finds the scopes in which a name occurs with a given role.
      *
      * @param symbol the symbol
      * @tparam T the role in which `symbol` occurs
      * @return all the scopes where `symbol` occurs with role `T`
      */
    def scopesForName[T <: Graph.Occurrence: ClassTag](
      symbol: String
    ): List[Graph.Scope] = {
      rootScope.scopesForSymbol[T](symbol)
    }

    /** Counts the number of scopes in this scope.
      *
      * @return the number of scopes that are either this scope or children of
      *         it
      */
    def numScopes: Int = {
      rootScope.numScopes
    }

    /** Determines the maximum nesting depth of scopes through this scope.
      *
      * @return the maximum nesting depth of scopes through this scope.
      */
    def nesting: Int = {
      rootScope.nesting
    }

    /** Determines if the provided ID shadows any other bindings.
      *
      * @param id the occurrence identifier
      * @return `true` if `id` shadows other bindings, otherwise `false`
      */
    def shadows(id: Graph.Id): Boolean = {
      scopeForId(id)
        .flatMap(
          scope =>
            scope.occurrence(id).flatMap {
              case d: Occurrence.Def => Some(d)
              case _                 => None
            }
        )
        .isDefined
    }
  }
  object Graph {

    /** The type of identifiers on the graph. */
    type Id = Int

    /** A representation of a local scope in Enso.
      *
      * @param childScopes all scopes that are _direct_ children of `this`
      * @param occurrences all symbol occurrences in `this` scope
      */
    sealed class Scope(
      private var childScopes: List[Scope]     = List(),
      private var occurrences: Set[Occurrence] = Set()
    ) {
      private var parent: Option[Scope] = None

      /** Creates and returns a scope that is a child of this one.
        *
        * @return a scope that is a child of `this`
        */
      def addChild(): Scope = {
        val scope = new Scope()
        scope.parent = Some(this)
        childScopes ::= scope

        scope
      }

      /** Adds the specified symbol occurrence to this scope.
        *
        * @param occurrence the occurrence to add
        */
      def addOccurrence(occurrence: Occurrence): Unit = {
        occurrences += occurrence
      }

      /** Finds an occurrence for the provided ID in the current scope, if it
        * exists.
        *
        * @param id the occurrence identifier
        * @return the occurrence for `id`, if it exists
        */
      def occurrence(id: Graph.Id): Option[Occurrence] = {
        occurrences.find(o => o.id == id)
      }

      /** Finds an occurrence for the provided symbol in the current scope, if
        * it exists.
        *
        * @param symbol the symbol of the occurrence
        * @return the occurrence for `name`, if it exists
        */
      def occurrence(symbol: String): Option[Occurrence] = {
        occurrences.find(o => o.symbol == symbol)
      }

      /** Resolves usages of symbols into links where possible, creating an edge
        * from the usage site to the definition site.
        *
        * @param occurrence the symbol usage
        * @param parentCounter the number of scopes that the link has traversed
        * @return the link from `occurrence` to the definition of that symbol, if it
        *         exists
        */
      def resolveUsage(
        occurrence: Graph.Occurrence.Use,
        parentCounter: Int = 0
      ): Option[Graph.Link] = {
        val definition = occurrences.find {
          case Graph.Occurrence.Def(_, n) => n == occurrence.symbol
          case _                          => false
        }

        definition match {
          case None =>
            parent.flatMap(_.resolveUsage(occurrence, parentCounter + 1))
          case Some(target) =>
            Some(Graph.Link(occurrence.id, parentCounter, target.id))
        }
      }

      /** Creates a string representation of the scope.
        *
        * @return a string representation of `this`
        */
      override def toString: String =
        s"Scope(childScopes = $childScopes, occurrences = $occurrences)"

      /** Counts the number of scopes in this scope.
        *
        * @return the number of scopes that are either this scope or children of
        *         it
        */
      def numScopes: Int = {
        childScopes.map(_.numScopes).sum + 1
      }

      /** Determines the maximum nesting depth of scopes through this scope.
        *
        * @return the maximum nesting depth of scopes through this scope.
        */
      def nesting: Int = {
        childScopes.map(_.nesting).foldLeft(0)(Math.max) + 1
      }

      /** Gets the scope where a given ID is defined in the graph.
        *
        * @param id the id to find the scope for
        * @return the scope where `id` occurs
        */
      def scopeForId(id: Graph.Id): Option[Scope] = {
        val possibleCandidates = occurrences.filter(o => o.id == id)

        if (possibleCandidates.size == 1) {
          Some(this)
        } else if (possibleCandidates.isEmpty) {
          val childCandidates = childScopes.map(_.scopeForId(id)).collect {
            case Some(scope) => scope
          }

          if (childCandidates.length == 1) {
            Some(childCandidates.head)
          } else if (childCandidates.isEmpty) {
            None
          } else {
            throw new CompilerError(s"ID $id defined in multiple scopes.")
          }
        } else {
          throw new CompilerError(s"Multiple occurrences found for ID $id.")
        }
      }

      /** Gets the n-th parent of `this` scope.
        *
        * @param n the number of scopes to walk up
        * @return the n-th parent of `this` scope, if present
        */
      def nThParent(n: Int): Option[Scope] = {
        if (n == 0) Some(this) else this.parent.flatMap(_.nThParent(n - 1))
      }

      /** Finds the scopes in which a symbol occurs with a given role.
        *
        * Users of this function _must_ explicitly specify `T`, otherwise the
        * results will be an empty list.
        *
        * @param symbol the symbol
        * @tparam T the role in which `name` occurs
        * @return all the scopes where `name` occurs with role `T`
        */
      def scopesForSymbol[T <: Occurrence: ClassTag](
        symbol: String
      ): List[Scope] = {
        val occursInThisScope = occurrences.collect {
          case nm: T if nm.symbol == symbol => nm
        }.nonEmpty

        val occurrencesInChildScopes =
          childScopes.flatMap(_.scopesForSymbol[T](symbol))

        if (occursInThisScope) {
          this +: occurrencesInChildScopes
        } else {
          occurrencesInChildScopes
        }
      }
    }

    /** A link in the [[Graph]].
      *
      * The source of the link should always be an [[Occurrence.Use]] while the
      * target of the link should always be an [[Occurrence.Def]].
      *
      * @param source the source ID of the link in the graph
      * @param scopeCount the number of scopes that the link traverses
      * @param target the target ID of the link in the graph
      */
    sealed case class Link(source: Id, scopeCount: Int, target: Id)

    /** An occurrence of a given symbol in the aliasing graph. */
    sealed trait Occurrence {
      val id: Id
      val symbol: String
    }
    object Occurrence {

      /** The definition of a symbol in the aliasing graph.
        *
        * @param id the identifier of the name in the graph
        * @param symbol the text of the name
        */
      sealed case class Def(id: Id, symbol: String) extends Occurrence

      /** A usage of a symbol in the aliasing graph
        *
        * Name usages _need not_ correspond to name definitions, as dynamic
        * symbol resolution means that a name used at runtime _may not_ be
        * statically visible in the scope.
        *
        * @param id the identifier of the name in the graph
        * @param symbol the text of the name
        */
      sealed case class Use(id: Id, symbol: String) extends Occurrence
    }
  }
}
