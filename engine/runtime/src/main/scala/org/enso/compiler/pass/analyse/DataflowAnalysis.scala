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
  override def runModule(ir: IR.Module): IR.Module = ir

  // TODO [AA] Work out how the expression flow can update the module and
  //  function metadata.
  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, annotated with data dependency information
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir

  // === Pass Internals =======================================================

  // === Pass Metadata ========================================================

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
    sealed class Function(
      val dependencies: mutable.Map[IR.Identifier, Set[IR.Identifier]] =
        mutable.Map()
    ) extends Dependencies
        with DependenciesImpl[IR.Identifier, Set[IR.Identifier]] {
      override val metadataName: String =
        "DataflowAnalysis.Dependencies.Function"

      /** Maps over the dependency mapping, producing a _new_ mapping.
        *
        * @param f the function to map over the dependency info
        * @tparam K2 the new key type
        * @tparam V2 the new value type
        * @return the result of mapping `f` over the dependency info
        */
      override def map[K2, V2](
        f: (Identifier, Set[Identifier]) => (K2, V2)
      ): DependenciesImpl[K2, V2] =
        new DependenciesImpl.Concrete[K2, V2](dependencies.map(f.tupled))

      /** Filters the dependency mapping, producing a _new_ mapping.
        *
        * @param f the filtering function
        * @return the result of filtering `this` using `f`
        */
      override def filter(
        f: (IR.Identifier, Set[IR.Identifier]) => Boolean
      ): Function = new Function(dependencies.filter(f.tupled))

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
      override def fold[A1, A2 >: (Identifier, Set[Identifier])](init: A1)(
        f: (A1, A2) => A1
      ): A1 = dependencies.foldLeft(init)(f)
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

      /** Maps over the dependency mapping, producing a _new_ mapping.
        *
        * @param f the function to map over the dependency info
        * @tparam K2 the new key type
        * @tparam V2 the new value type
        * @return the result of mapping `f` over the dependency info
        */
      override def map[K2, V2](
        f: (String, Set[Identifier]) => (K2, V2)
      ): DependenciesImpl[K2, V2] =
        new DependenciesImpl.Concrete[K2, V2](dependencies.map(f.tupled))

      /** Filters the dependency mapping, producing a _new_ mapping.
        *
        * @param f the filtering function
        * @return the result of filtering `this` using `f`
        */
      override def filter(
        f: (String, Set[Identifier]) => Boolean
      ): DependenciesImpl[String, Set[Identifier]] =
        new Module(dependencies.filter(f.tupled))

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
      override def fold[A1, A2 >: (String, Set[Identifier])](init: A1)(
        f: (A1, A2) => A1
      ): A1 = dependencies.foldLeft(init)(f)
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
    def map[K2, V2](f: (K, V) => (K2, V2)): DependenciesImpl[K2, V2]

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
    def fold[A1, A2 >: (K, V)](init: A1)(f: (A1, A2) => A1): A1
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
      val dependencies: mutable.Map[K, V] = mutable.Map()
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
