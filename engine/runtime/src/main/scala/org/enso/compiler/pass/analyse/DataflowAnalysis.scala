package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Identifier
import org.enso.compiler.pass.IRPass

import scala.collection.mutable

/** This pass implements dataflow analysis for Enso.
  *
  * Dataflow analysis is the processes of determining the dependencies between
  * program expressions.
  *
  * This pass needs to be run after [[AliasAnalysis]], [[DemandAnalysis]], and
  * [[TailCall]]. It also assumes that all members of [[IR.IRKind.Primitive]]
  * have been removed from the IR by the time it runs.
  */
case object DataflowAnalysis extends IRPass {
  override type Metadata = Dependencies

  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, annotated with data dependency information
    */
  override def runModule(ir: IR.Module): IR.Module = {
    val moduleDepInfo = new Dependencies.Module

    ir.copy(
        bindings =
          ir.bindings.map(d => analyseModuleDefinition(d, moduleDepInfo))
      )
      .addMetadata(moduleDepInfo)
  }

  // TODO [AA] Maybe make this work for the hell of it -> if the context is
  //  there it should just work.
  /** A no-op.
    *
    * At the current time we do not support caching in the inline expression
    * flow, and hence this pass doesn't run in the expression flow.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir

  // === Pass Internals =======================================================

  def analyseModuleDefinition(
    binding: IR.Module.Scope.Definition,
    moduleDepInfo: Dependencies.Module
  ): IR.Module.Scope.Definition = {
    binding match {
      case atom: IR.Module.Scope.Definition.Atom => atom
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        val functionDepInfo = new Dependencies.Scope
        m.copy(body =
          analyseExpression(body, moduleDepInfo, List(functionDepInfo))
        )
    }
  }

  // TODO [AA] Which nodes should get the `functionDepInfo`?
  def analyseExpression(
    expression: IR.Expression,
    moduleDepInfo: Dependencies.Module,
    scopeStack: List[Dependencies.Scope]
  ): IR.Expression = {
    expression
  }

  // === Pass Metadata ========================================================

  // TODO [AA] Nested query function.
  // TODO [AA] `Function` should be attached to each scope, but upper scopes
  //  should contain the data of lower scopes. Should get Inserted into
  //  `LocalScope`
  // TODO [AA] Some way of identifying things. Note, this pass doesn't attempt
  //  to deal with the fact that IDs change on code update.
  // TODO [AA] Need to produce global data.
  //  - Function Body, global symbol
  //  - Two types of metadata (one global attached to modules, and one local
  //    attached to functions)
  //  - Interdependent global symbols as a problem

  /** This metadata describes which expressions depend on a given expression. */
  sealed trait Dependencies extends IR.Metadata
  object Dependencies {

    /** Function-level dataflow results.
      *
      * It contains a mapping from expression identifiers to the identifiers of
      * all other expressions that depend on a given expression.
      *
      * @param dependencies the mapping between dependencies
      */
    sealed class Scope(
      val dependencies: mutable.Map[IR.Identifier, Set[IR.Identifier]] =
        mutable.Map()
    ) extends Dependencies
        with DependenciesImpl[IR.Identifier, Set[IR.Identifier]] {
      override val metadataName: String =
        "DataflowAnalysis.Dependencies.Scope"

      /** Filters the dependency mapping, producing a _new_ mapping.
        *
        * @param f the filtering function
        * @return the result of filtering `this` using `f`
        */
      override def filter(
        f: (IR.Identifier, Set[IR.Identifier]) => Boolean
      ): Scope = new Scope(dependencies.filter(f.tupled))

      /** Returns the set of identifiers that should be invalidated when the
        * input identifier is changed.
        *
        * @param id the identifier being changed
        * @return the set of identifiers that should be invalidated when `id` is
        *         changed
        */
      def shouldInvalidateWhenChanging(
        id: IR.Identifier
      ): Set[IR.Identifier] = {
        ???
      }
    }

    /** Module-level dataflow results.
      *
      * It contains a mapping from the names of dynamic symbols to the
      * identifiers of all other expressions that depend on that dynamic symbol.
      *
      * @param dependencies the mapping between dependencies
      */
    sealed class Module(
      val dependencies: mutable.Map[String, Set[IR.Identifier]] = mutable.Map()
    ) extends Dependencies
        with DependenciesImpl[String, Set[IR.Identifier]] {
      override val metadataName: String = "DafaflowAnalysis.Dependencies.Module"

      /** Filters the dependency mapping, producing a _new_ mapping.
        *
        * @param f the filtering function
        * @return the result of filtering `this` using `f`
        */
      override def filter(
        f: (String, Set[Identifier]) => Boolean
      ): DependenciesImpl[String, Set[Identifier]] =
        new Module(dependencies.filter(f.tupled))

      /** Combines the analysis results for two modules.
        *
        * @param that the other module result to combine with `this`
        * @return the result of combining `this` and `that`
        */
      def ++(that: Module): Module = {
        val combinedModule = new Module(this.dependencies)

        for ((key, value) <- that.dependencies) {
          combinedModule.get(key) match {
            case Some(xs) => combinedModule(key) = value ++ xs
            case None     => combinedModule(key) = value
          }
        }

        combinedModule
      }

      /** Returns the set of identifiers that should be invalidated when the
        * input symbol is changed.
        *
        * @param symbol the dynamic symbol being changed
        * @return the set of identifiers that should be invalidated when
        *         `symbol` is changed
        */
      def shouldInvalidateWhenChanging(
        symbol: IR.Identifier
      ): Set[IR.Identifier] = {
        ???
      }
    }
  }

  sealed trait DependenciesImpl[K, V] {

    /** Storage for the dependents data. */
    val dependencies: mutable.Map[K, V]

    /** Provides access to all of the dependents for a given identifier.
      *
      * @param key the identifier to find dependents of
      * @return the dependents of the expression denoted by `key`
      * @throws NoSuchElementException if `key` does not exist
      */
    def apply(key: K): V = dependencies(key)

    /** Safely gets the dependents for a given identifier.
      *
      * @param key the identifier to find dependents of
      * @return the dependents of the expression denoted by `key`, if such
      *         an expression exists
      */
    def get(key: K): Option[V] = dependencies.get(key)

    /** Updates the value for the provided key.
      *
      * @param key the key to update
      * @param value the value to associate with `key`
      */
    def update(key: K, value: V): Unit = dependencies(key) = value

    /** Removes the given identifier and its dependents from the mapping.
      *
      * @param key the identifier to remove data for
      * @return the removed dependents of the expression denoted by `key`
      */
    def remove(key: K): Option[V] = dependencies.remove(key)

    /** Checks whether a value matching the predicate exists in the data.
      *
      * @param pred the predicate to satisfy
      * @return whather or not `pred` has been satisfied
      */
    def exists(pred: (K, V) => Boolean): Boolean =
      dependencies.exists(pred.tupled)

    /** Maps over the dependency mapping, producing a _new_ mapping.
      *
      * @param f the function to map over the dependency info
      * @tparam K2 the new key type
      * @tparam V2 the new value type
      * @return the result of mapping `f` over the dependency info
      */
    def map[K2, V2](f: (K, V) => (K2, V2)): DependenciesImpl[K2, V2] =
      new DependenciesImpl.Concrete[K2, V2](dependencies.map(f.tupled))

    /** Filters the dependency mapping, producing a _new_ mapping.
      *
      * @param f the filtering function
      * @return the result of filtering `this` using `f`
      */
    def filter(f: (K, V) => Boolean): DependenciesImpl[K, V]

    /** Folds over the dependency mapping.
      *
      * This is a left fold.
      *
      * @param init the initial value for the fold
      * @param f the fold function
      * @tparam A1 the left operand type
      * @tparam A2 the right operand type
      * @return the result of folding over `this` using `f` starting at `init`
      */
    def fold[A1, A2 >: (K, V)](init: A1)(f: (A1, A2) => A1): A1 =
      dependencies.foldLeft(init)(f)
  }
  object DependenciesImpl {

    /** An abitrary dependencies container for when it's necessary to use the
      * type-changing map.
      *
      * @param dependencies the dependency information
      * @tparam K the key type in the map
      * @tparam V the value type in the map
      */
    sealed class Concrete[K, V](
      val dependencies: mutable.Map[K, V] = mutable.Map[K, V]()
    ) extends DependenciesImpl[K, V] {

      /** Maps over the dependency mapping, producing a _new_ mapping.
        *
        * @param f the function to map over the dependency info
        * @tparam K2 the new key type
        * @tparam V2 the new value type
        * @return the result of mapping `f` over the dependency info
        */
      override def map[K2, V2](f: (K, V) => (K2, V2)): Concrete[K2, V2] =
        new Concrete(dependencies.map(f.tupled))

      /** Filters the dependency mapping, producing a _new_ mapping.
        *
        * @param f the filtering function
        * @return the result of filtering `this` using `f`
        */
      override def filter(f: (K, V) => Boolean): Concrete[K, V] =
        new Concrete(dependencies.filter(f.tupled))

      /** Folds over the dependency mapping.
        *
        * This is a left fold.
        *
        * @param init the initial value for the fold
        * @param f the fold function
        * @tparam A1 the left operand type
        * @tparam A2 the right operand type
        * @return the result of folding over `this` using `f` starting at `init`
        */
      override def fold[A1, A2 >: (K, V)](init: A1)(f: (A1, A2) => A1): A1 =
        dependencies.foldLeft(init)(f)
    }
  }
}
