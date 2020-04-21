package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
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
  override type Metadata = Dependency

  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, annotated with data dependency information
    */
  override def runModule(ir: IR.Module): IR.Module = {
    ir
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
    binding: IR.Module.Scope.Definition
  ): IR.Module.Scope.Definition = {
    binding match {
      case atom: IR.Module.Scope.Definition.Atom => atom
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        m.copy(body = analyseExpression(body))
    }
  }

  def analyseExpression(
    expression: IR.Expression
  ): IR.Expression = {
    expression
  }

  // === Pass Metadata ========================================================

  /** Storage for dependency information.
    *
    * @param dependencies storage for the direct dependencies between program
    *                     components
    */
  sealed case class Dependency(
    dependencies: mutable.Map[Dependency.Type, Set[Dependency.Type]] =
      mutable.Map()
  ) extends IR.Metadata {
    override val metadataName: String = "DataflowAnalysis.Dependencies"

    /** Returns the set of all dependents for the provided key.
      *
      * Please note that the result set contains not just the _direct_ dependets
      * of the key, but _all_ dependents of the key.
      *
      * @param key the key to get the dependents of
      * @return the set of all dependencies on `key`
      * @throws NoSuchElementException when `key` does not exist in the
      *                                dependencies mapping
      */
    @throws[NoSuchElementException]
    def apply(key: Dependency.Type): Set[Dependency.Type] = {
      if (dependencies.contains(key)) {
        get(key) match {
          case Some(deps) => deps
          case None       => throw new NoSuchElementException
        }
      } else {
        throw new NoSuchElementException
      }
    }

    /** Returns the set of all dependents for the provided identifier.
      *
      * Please note that the result set contains not just the _direct_
      * dependents of the id, but _all_ dependents of the id.
      *
      * @param id the key to get the dependents of
      * @return the set of all dependencies on `id`
      * @throws NoSuchElementException when `key` does not exist in the
      *                                dependencies mapping
      */
    @throws[NoSuchElementException]
    def apply(id: Dependency.Identifier): Set[Dependency.Type] =
      this.apply(Dependency.Type.Static(id))

    /** Returns the set of all dependents for the provided symbol name.
      *
      * Please note that the result set contains not just the _direct_
      * dependents of the symbol, but _all_ dependents of the symbol.
      *
      * @param symbol the key to get the dependents of
      * @return the set of all dependencies on `symbol`
      * @throws NoSuchElementException when `key` does not exist in the
      *                                dependencies mapping
      */
    @throws[NoSuchElementException]
    def apply(symbol: Dependency.Symbol): Set[Dependency.Type] =
      this.apply(Dependency.Type.Dynamic(symbol))

    /** Safely gets the set of all dependents for the provided key.
      *
      * Please note that the result set contains not just the _direct_ dependents
      * of the key, but _all_ dependents of the key.
      *
      * @param key the key to get the dependents of
      * @return the set of all dependencies on `key`, if key exists
      */
    def get(key: Dependency.Type): Option[Set[Dependency.Type]] = {
      val visited = mutable.Set[Dependency.Type]()

      def go(key: Dependency.Type): Set[Dependency.Type] = {
        if (!visited.contains(key)) {
          visited += key

          dependencies.get(key) match {
            case Some(deps) => deps ++ deps.map(go).reduceLeft(_ ++ _)
            case None       => Set()
          }
        } else {
          Set()
        }
      }

      if (dependencies.contains(key)) {
        Some(go(key))
      } else {
        None
      }
    }

    /** Returns the set of all dependents for the provided identifier.
      *
      * Please note that the result set contains not just the _direct_ dependents
      * of the id, but _all_ dependents of the id.
      *
      * @param id the key to get the dependents of
      * @return the set of all dependencies on `id`, if it exists
      */
    def get(id: Dependency.Identifier): Option[Set[Dependency.Type]] =
      get(Dependency.Type.Static(id))

    /** Returns the set of all dependents for the provided symbol name.
      *
      * Please note that the result set contains not just the _direct_ dependents
      * of the symbol, but _all_ dependents of the symbol.
      *
      * @param symbol the key to get the dependents of
      * @return the set of all dependencies on `symbol`
      */
    def get(symbol: Dependency.Symbol): Option[Set[Dependency.Type]] =
      get(Dependency.Type.Dynamic(symbol))

    /** Executes an update on the dependency information.
      *
      * @param key the key to update the dependents for
      * @param dependents the updated dependents for `key`
      */
    def update(key: Dependency.Type, dependents: Set[Dependency.Type]): Unit =
      dependencies(key) = dependents

    /** Executes an update on the dependency information.
      *
      * @param id the key to update the dependents for
      * @param dependents the updated dependents for `id`
      */
    def update(
      id: Dependency.Identifier,
      dependents: Set[Dependency.Type]
    ): Unit =
      update(Dependency.Type.Static(id), dependents)

    /** Executes an update on the dependency information.
      *
      * @param symbol the key to update the dependents for
      * @param dependents the updated dependents for `symbol`
      */
    def update(
      symbol: Dependency.Symbol,
      dependents: Set[Dependency.Type]
    ): Unit =
      update(Dependency.Type.Dynamic(symbol), dependents)

    /** Updates the dependents for the provided key, or creates them if they do
      * not already exist.
      *
      * @param key the key to add or update dependents for
      * @param dependents the new dependents information for `key`
      */
    def updateAt(
      key: Dependency.Type,
      dependents: Set[Dependency.Type]
    ): Unit = {
      if (dependencies.contains(key)) {
        dependencies(key) ++= dependents
      } else {
        dependencies(key) = dependents
      }
    }

    /** Updates the dependents for the provided id, or creates them if they do
      * not already exist.
      *
      * @param id the key to add or update dependents for
      * @param dependents the new dependents information for `id`
      */
    def updateAt(
      id: DataflowAnalysis.Dependency.Identifier,
      dependents: Set[Dependency.Type]
    ): Unit = {
      updateAt(Dependency.Type.Static(id), dependents)
    }

    /** Updates the dependents for the provided symbol, or creates them if they
      * do not already exist.
      *
      * @param symbol the key to add or update dependents for
      * @param dependents the new dependents information for `symbol`
      */
    def updateAt(
      symbol: Dependency.Symbol,
      dependents: Set[Dependency.Type]
    ): Unit = {
      updateAt(Dependency.Type.Dynamic(symbol), dependents)
    }

    /** Combines two dependency information containers.
      *
      * @param that the other contaoner to combine with `this`
      * @return the result of combining `this` and `that`
      */
    def ++(that: Dependency): Dependency = {
      val combinedModule = new Dependency(this.dependencies)

      for ((key, value) <- that.dependencies) {
        combinedModule.dependencies.get(key) match {
          case Some(xs) => combinedModule(key) = value ++ xs
          case None     => combinedModule(key) = value
        }
      }

      combinedModule
    }
  }
  object Dependency {

    /** The type of identifiers in this analysis. */
    type Identifier = IR.Identifier

    /** The type of symbols in this analysis. */
    type Symbol = String

    /** The type of identification for a program component. */
    sealed trait Type
    object Type {

      /** Program components identified by their unique identifier.
        *
        * @param id the unique identifier of the program component
        */
      sealed case class Static(id: Dependency.Identifier) extends Type

      /** Program components identified by their symbol.
        *
        * @param name the name of the symbol
        */
      sealed case class Dynamic(name: Dependency.Symbol) extends Type
    }
  }
}
