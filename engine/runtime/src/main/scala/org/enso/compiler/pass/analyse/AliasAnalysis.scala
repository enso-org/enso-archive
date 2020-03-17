package org.enso.compiler.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Occurrence, Scope}
import org.enso.interpreter.runtime.scope.{LocalScope, ModuleScope}

import scala.reflect.ClassTag

/** This pass performs scope identification and analysis, as well as symbol
  * resolution where it is possible to do so statically.
  *
  * It attaches the following information to the IR:
  *
  * - Top-level constructs are annotated with an aliasing graph.
  * - Scopes within each top-level construct are annotated with the
  *   corresponding child scope.
  * - Occurrences of symbols are annotated with occurrence information that
  *   points into the graph.
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
              lambdaReuseScope = true,
              blockReuseScope  = true
            )
          )
          .addMetadata(Info.Scope.Root(topLevelGraph))
      case a @ IR.Module.Scope.Definition.Atom(_, args, _, _) =>
        a.copy(
            arguments = analyseArgumentDefs(
              args,
              topLevelGraph,
              topLevelGraph.rootScope
            )
          )
          .addMetadata(Info.Scope.Root(topLevelGraph))
    }
  }

  /* TODO [AA]
   * - Method (Lambda (Block)) -> collapse to 1
   * - Method (Block) -> Collapse to 1
   * - Method (Lambda) -> Collapse without lambda body
   * - Lambda (Block) -> collapse to 1
   *
   * `lambdaReuseScope`
   * `blockReuseScope`
   */
  def analyseExpression(
    expression: IR.Expression,
    graph: Graph,
    parentScope: Scope,
    lambdaReuseScope: Boolean = false,
    blockReuseScope: Boolean  = false
  ): IR.Expression = {
    expression match {
      case fn: IR.Function =>
        analyseFunction(fn, graph, parentScope, lambdaReuseScope)
      case name: IR.Name => analyseName(name, graph, parentScope)
      case cse: IR.Case =>
        analyseCase(cse, graph, parentScope, blockReuseScope)
      case block @ IR.Expression.Block(expressions, retVal, _, _, _) =>
        val currentScope =
          if (blockReuseScope) parentScope else parentScope.addChild()

        block
          .copy(
            expressions = expressions.map(
              (expression: IR.Expression) =>
                analyseExpression(
                  expression,
                  graph,
                  currentScope
                )
            ),
            returnValue = analyseExpression(
              retVal,
              graph,
              currentScope
            )
          )
          .addMetadata(Info.Scope.Child(graph, currentScope))
      case binding @ IR.Expression.Binding(name, expression, _, _) =>
        if (!parentScope.hasSymbolOccurrenceAs[Occurrence.Def](name.name)) {
          val occurrenceId = graph.nextId()
          val occurrence   = Occurrence.Def(occurrenceId, name.name)

          parentScope.add(occurrence)

          binding
            .copy(
              expression = analyseExpression(
                expression,
                graph,
                parentScope
              )
            )
            .addMetadata(Info.Occurrence(graph, occurrenceId))
        } else {
          IR.Error.Redefined.Binding(binding)
        }
      case x =>
        x.mapExpressions(
          (expression: IR.Expression) =>
            analyseExpression(
              expression,
              graph,
              parentScope
            )
        )
    }
  }

  def analyseArgumentDefs(
    args: List[IR.DefinitionArgument],
    graph: Graph,
    parentScope: Scope
  ): List[IR.DefinitionArgument] = {
    args.map {
      case arg @ IR.DefinitionArgument.Specified(name, value, _, _, _) =>
        if (!parentScope.hasSymbolOccurrenceAs[Occurrence.Def](name.name)) {
          val occurrenceId = graph.nextId()
          parentScope.add(Graph.Occurrence.Def(occurrenceId, name.name))

          arg
            .copy(
              defaultValue = value.map(
                (ir: IR.Expression) =>
                  analyseExpression(
                    ir,
                    graph,
                    parentScope
                  )
              )
            )
            .addMetadata(Info.Occurrence(graph, occurrenceId))
        } else {
          IR.Error.Redefined.Argument(arg)
        }
      case err: IR.Error.Redefined.Argument => err
    }
  }

  // TODO [AA] This behaves as if multi-argument lambdas are real. This should
  //  be fixed at some point.
  def analyseFunction(
    function: IR.Function,
    graph: Graph,
    parentScope: Scope,
    lambdaReuseScope: Boolean = false
  ): IR.Function = {
    val currentScope =
      if (lambdaReuseScope) parentScope else parentScope.addChild()

    function match {
      case lambda @ IR.Function.Lambda(arguments, body, _, _, _) =>
        lambda
          .copy(
            arguments = analyseArgumentDefs(arguments, graph, currentScope),
            body = analyseExpression(
              body,
              graph,
              currentScope,
              blockReuseScope = true
            )
          )
          .addMetadata(Info.Scope.Child(graph, currentScope))
    }
  }

  def analyseName(
    name: IR.Name,
    graph: Graph,
    parentScope: Scope
  ): IR.Name = {
    val occurrenceId = graph.nextId()
    val occurrence   = Occurrence.Use(occurrenceId, name.name)

    parentScope.add(occurrence)
    graph.resolveUsage(occurrence)

    name.addMetadata(Info.Occurrence(graph, occurrenceId))
  }

  def analyseCase(
    ir: IR.Case,
    graph: Graph,
    parentScope: Scope,
    caseReuseScope: Boolean
  ): IR.Case = {
    ir match {
      case caseExpr @ IR.Case.Expr(scrutinee, branches, fallback, _, _) =>
        val currentScope =
          if (caseReuseScope) parentScope else parentScope.addChild()

        caseExpr
          .copy(
            scrutinee = analyseExpression(
              scrutinee,
              graph,
              currentScope,
              caseReuseScope
            ),
            branches = branches.map(branch => {
              branch.copy(
                pattern = analyseExpression(
                  branch.pattern,
                  graph,
                  currentScope
                ),
                expression = analyseExpression(
                  branch.expression,
                  graph,
                  currentScope
                )
              )
            }),
            fallback = fallback.map(
              (expression: IR.Expression) =>
                analyseExpression(
                  expression,
                  graph,
                  currentScope
                )
            )
          )
          .addMetadata(Info.Scope.Child(graph, currentScope))
      case _ => throw new CompilerError("Case branch in `analyseCase`.")
    }
  }

  // === Data Definitions =====================================================

  /** Information about the aliasing state for a given IR node. */
  sealed trait Info extends IR.Metadata
  object Info {
    sealed trait Scope extends Info
    object Scope {

      /** Aliasing information for a root scope.
        *
        * A root scope has a 1:1 correspondence with a
        * [[org.enso.interpreter.node.EnsoRootNode]].
        *
        * @param graph the graph containing the alias information for that node
        */
      sealed case class Root(graph: Graph) extends Scope

      /** Aliasing information about a child scope.
        *
        * @param graph the graph
        * @param scope the child scope in `graph`
        */
      sealed case class Child(graph: Graph, scope: Graph.Scope) extends Scope
    }

    /** Aliasing information for a piece of [[IR]] that is contained within a
      * [[Scope]].
      *
      * @param graph the graph in which this IR node can be found
      * @param id the identifier of this IR node in `graph`
      */
    sealed case class Occurrence(graph: Graph, id: Graph.Id) extends Info
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
      * @return the link, if it exists
      */
    def resolveUsage(
      occurrence: Graph.Occurrence.Use
    ): Option[Graph.Link] = {
      val scope = scopeFor(occurrence.id)

      scope.flatMap {
        _.resolveUsage(occurrence)
          .flatMap(link => {
            links += link
            Some(link)
          })
      }
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
    def linksFor(id: Graph.Id): Set[Graph.Link] = {
      links.filter(l => l.source == id || l.target == id)
    }

    /** Gets the scope where a given ID is defined in the graph.
      *
      * @param id the id to find the scope for
      * @return the scope where `id` occurs
      */
    def scopeFor(id: Graph.Id): Option[Graph.Scope] = {
      rootScope.findScope(id)
    }

    /** Finds the scopes in which a name occurs with a given role.
      *
      * @param symbol the symbol
      * @tparam T the role in which `symbol` occurs
      * @return all the scopes where `symbol` occurs with role `T`
      */
    def scopesFor[T <: Graph.Occurrence: ClassTag](
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
      rootScope.scopeCount
    }

    /** Determines the maximum nesting depth of scopes through this scope.
      *
      * @return the maximum nesting depth of scopes through this scope.
      */
    def nesting: Int = {
      rootScope.maxNesting
    }

    /** Determines if the provided ID shadows any other bindings.
      *
      * @param id the occurrence identifier
      * @return `true` if `id` shadows other bindings, otherwise `false`
      */
    def shadows(id: Graph.Id): Boolean = {
      scopeFor(id)
        .flatMap(
          _.getOccurrence(id).flatMap {
            case d: Occurrence.Def => Some(d)
            case _                 => None
          }
        )
        .isDefined
    }

    /** Determines if the provided symbol shadows any other bindings.
      *
      * @param symbol the symbol
      * @return `true` if `symbol` shadows other bindings, otherwise `false`
      */
    def shadows(symbol: String): Boolean = {
      scopesFor[Occurrence.Def](symbol).nonEmpty
    }

    /** Gets all symbols defined in the graph.
      *
      * @return the set of symbols defined in this graph
      */
    def symbols: Set[String] = {
      rootScope.symbols
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
      def add(occurrence: Occurrence): Unit = {
        occurrences += occurrence
      }

      /** Finds an occurrence for the provided ID in the current scope, if it
        * exists.
        *
        * @param id the occurrence identifier
        * @return the occurrence for `id`, if it exists
        */
      def getOccurrence(id: Graph.Id): Option[Occurrence] = {
        occurrences.find(o => o.id == id)
      }

      /** Finds any occurrences for the provided symbol in the current scope, if
        * it exists.
        *
        * @param symbol the symbol of the occurrence
        * @return the occurrences for `name`, if they exist
        */
      def getOccurrences(symbol: String): Set[Occurrence] = {
        occurrences.filter(o => o.symbol == symbol)
      }

      /** Unsafely gets the occurrence for the provided ID in the current scope.
        *
        * Please note that this will crash if the ID is not defined in this
        * scope.
        *
        * @param id the occurrence identifier
        * @return the occurrence for `id`
        */
      def unsafeGetOccurrence(id: Graph.Id): Occurrence = {
        getOccurrence(id).get
      }

      /** Checks whether a symbol occurs in a given role in the current scope.
        *
        * @param symbol the symbol to check for
        * @tparam T the role for it to occur in
        * @return `true` if `symbol` occurs in role `T` in this scope, `false`
        *         otherwise
        */
      def hasSymbolOccurrenceAs[T <: Occurrence: ClassTag](
        symbol: String
      ): Boolean = {
        occurrences.collect { case x: T if x.symbol == symbol => x }.nonEmpty
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
      def scopeCount: Int = {
        childScopes.map(_.scopeCount).sum + 1
      }

      /** Determines the maximum nesting depth of scopes through this scope.
        *
        * @return the maximum nesting depth of scopes through this scope.
        */
      def maxNesting: Int = {
        childScopes.map(_.maxNesting).foldLeft(0)(Math.max) + 1
      }

      /** Gets the scope where a given ID is defined in the graph.
        *
        * @param id the id to find the scope for
        * @return the scope where `id` occurs
        */
      def findScope(id: Graph.Id): Option[Scope] = {
        val possibleCandidates = occurrences.filter(o => o.id == id)

        if (possibleCandidates.size == 1) {
          Some(this)
        } else if (possibleCandidates.isEmpty) {
          val childCandidates = childScopes.map(_.findScope(id)).collect {
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
        val occursInThisScope = hasSymbolOccurrenceAs[T](symbol)

        val occurrencesInChildScopes =
          childScopes.flatMap(_.scopesForSymbol[T](symbol))

        if (occursInThisScope) {
          this +: occurrencesInChildScopes
        } else {
          occurrencesInChildScopes
        }
      }

      /** Gets the set of all symbols in this scope and its children.
        *
        * @return the set of symbols
        */
      def symbols: Set[String] = {
        val symbolsInThis        = occurrences.map(_.symbol)
        val symbolsInChildScopes = childScopes.flatMap(_.symbols)

        symbolsInThis ++ symbolsInChildScopes
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
