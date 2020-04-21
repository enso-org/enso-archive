package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis.{Info => AliasInfo}
import org.enso.syntax.text.Debug

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
  override type Metadata = DependencyInfo

  /** Executes the dataflow analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, annotated with data dependency information
    */
  override def runModule(ir: IR.Module): IR.Module = {
    val dependencyInfo = new DependencyInfo
    ir.copy(
        bindings = ir.bindings.map(analyseModuleDefinition(_, dependencyInfo))
      )
      .addMetadata(dependencyInfo)
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
    dependencyInfo: DependencyInfo
  ): IR.Module.Scope.Definition = {
    binding match {
      case atom @ IR.Module.Scope.Definition.Atom(_, arguments, _, _) =>
        atom
          .copy(
            arguments =
              arguments.map(analyseDefinitionArgument(_, dependencyInfo))
          )
          .addMetadata(dependencyInfo)
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        m.copy(
            body = analyseExpression(body, dependencyInfo)
          )
          .addMetadata(dependencyInfo)
    }
  }

  def analyseExpression(
    expression: IR.Expression,
    dependencyInfo: DependencyInfo
  ): IR.Expression = {
    // TODO [AA] How should this pass be structured?
    expression match {
      case function: IR.Function => analyseFunction(function, dependencyInfo)
      case app: IR.Application   => analyseApplication(app, dependencyInfo)
      case typ: IR.Type          => analyseType(typ, dependencyInfo)
      case name: IR.Name         => analyseName(name, dependencyInfo)
      case cse: IR.Case          => analyseCase(cse, dependencyInfo)
      case literal: IR.Literal   => analyseLiteral(literal, dependencyInfo)
      case foreign: IR.Foreign   => analyseForeign(foreign, dependencyInfo)
      case comment: IR.Comment   => analyseComment(comment, dependencyInfo)

      case block @ IR.Expression.Block(expressions, returnValue, _, _, _) =>
        // TODO [AA] Block's value depends on its return value (ret val may
        //  depend on other exprs)
        block
      case binding @ IR.Expression.Binding(name, expression, _, _) =>
        // TODO [AA]
        binding

      case error: IR.Error => error
    }
  }

  def analyseFunction(
    function: IR.Function,
    info: DependencyInfo
  ): IR.Function = {
    // TODO [AA]
    function
  }

  def analyseApplication(
    application: IR.Application,
    info: DependencyInfo
  ): IR.Application = {
    // TODO [AA]
    application
  }

  def analyseType(typ: IR.Type, info: DependencyInfo): IR.Type = {
    // TODO [AA]
    typ
  }

  def analyseName(name: IR.Name, info: DependencyInfo): IR.Name = {
    // TODO [AA]
    name
  }

  def analyseCase(cse: IR.Case, info: DependencyInfo): IR.Case = {
    // TODO [AA]
    cse
  }

  def analyseLiteral(literal: IR.Literal, info: DependencyInfo): IR.Literal = {
    // TODO [AA]
    literal
  }

  def analyseForeign(foreign: IR.Foreign, info: DependencyInfo): IR.Foreign = {
    // TODO [AA]
    foreign
  }

  def analyseComment(comment: IR.Comment, info: DependencyInfo): IR.Comment = {
    // TODO [AA]
    comment
  }

  def analyseDefinitionArgument(
    argument: IR.DefinitionArgument,
    dependencyInfo: DependencyInfo
  ): IR.DefinitionArgument = {
    // TODO [AA]
    argument
  }

  def analyseCallArgument(
    argument: IR.CallArgument,
    dependencyInfo: DependencyInfo
  ): IR.CallArgument = {
    // TODO [AA]
    argument
  }

  // === Pass Metadata ========================================================

  /** Storage for dependency information.
    *
    * @param dependencies storage for the direct dependencies between program
    *                     components
    */
  sealed case class DependencyInfo(
    dependencies: mutable.Map[DependencyInfo.Type, Set[DependencyInfo.Type]] =
      mutable.Map()
  ) extends IR.Metadata {
    override val metadataName: String = "DataflowAnalysis.Dependencies"

    /** Returns the set of all dependents for the provided key.
      *
      * Please note that the result set contains not just the _direct_
      * dependents of the key, but _all_ dependents of the key.
      *
      * @param key the key to get the dependents of
      * @return the set of all dependencies on `key`
      * @throws NoSuchElementException when `key` does not exist in the
      *                                dependencies mapping
      */
    @throws[NoSuchElementException]
    def apply(key: DependencyInfo.Type): Set[DependencyInfo.Type] = {
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
    def apply(id: DependencyInfo.Identifier): Set[DependencyInfo.Type] =
      this.apply(DependencyInfo.Type.Static(id))

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
    def apply(symbol: DependencyInfo.Symbol): Set[DependencyInfo.Type] =
      this.apply(DependencyInfo.Type.Dynamic(symbol))

    /** Safely gets the set of all dependents for the provided key.
      *
      * Please note that the result set contains not just the _direct_ dependents
      * of the key, but _all_ dependents of the key.
      *
      * @param key the key to get the dependents of
      * @return the set of all dependencies on `key`, if key exists
      */
    def get(key: DependencyInfo.Type): Option[Set[DependencyInfo.Type]] = {
      val visited = mutable.Set[DependencyInfo.Type]()

      def go(key: DependencyInfo.Type): Set[DependencyInfo.Type] = {
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
      * Please note that the result set contains not just the _direct_
      * dependents of the id, but _all_ dependents of the id.
      *
      * @param id the key to get the dependents of
      * @return the set of all dependencies on `id`, if it exists
      */
    def get(id: DependencyInfo.Identifier): Option[Set[DependencyInfo.Type]] =
      get(DependencyInfo.Type.Static(id))

    /** Returns the set of all dependents for the provided symbol name.
      *
      * Please note that the result set contains not just the _direct_
      * dependents of the symbol, but _all_ dependents of the symbol.
      *
      * @param symbol the key to get the dependents of
      * @return the set of all dependencies on `symbol`
      */
    def get(symbol: DependencyInfo.Symbol): Option[Set[DependencyInfo.Type]] =
      get(DependencyInfo.Type.Dynamic(symbol))

    /** Executes an update on the dependency information.
      *
      * @param key the key to update the dependents for
      * @param dependents the updated dependents for `key`
      */
    def update(
      key: DependencyInfo.Type,
      dependents: Set[DependencyInfo.Type]
    ): Unit =
      dependencies(key) = dependents

    /** Executes an update on the dependency information.
      *
      * @param id the key to update the dependents for
      * @param dependents the updated dependents for `id`
      */
    def update(
      id: DependencyInfo.Identifier,
      dependents: Set[DependencyInfo.Type]
    ): Unit =
      update(DependencyInfo.Type.Static(id), dependents)

    /** Executes an update on the dependency information.
      *
      * @param symbol the key to update the dependents for
      * @param dependents the updated dependents for `symbol`
      */
    def update(
      symbol: DependencyInfo.Symbol,
      dependents: Set[DependencyInfo.Type]
    ): Unit =
      update(DependencyInfo.Type.Dynamic(symbol), dependents)

    /** Updates the dependents for the provided key, or creates them if they do
      * not already exist.
      *
      * @param key the key to add or update dependents for
      * @param dependents the new dependents information for `key`
      */
    def updateAt(
      key: DependencyInfo.Type,
      dependents: Set[DependencyInfo.Type]
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
      id: DataflowAnalysis.DependencyInfo.Identifier,
      dependents: Set[DependencyInfo.Type]
    ): Unit = {
      updateAt(DependencyInfo.Type.Static(id), dependents)
    }

    /** Updates the dependents for the provided symbol, or creates them if they
      * do not already exist.
      *
      * @param symbol the key to add or update dependents for
      * @param dependents the new dependents information for `symbol`
      */
    def updateAt(
      symbol: DependencyInfo.Symbol,
      dependents: Set[DependencyInfo.Type]
    ): Unit = {
      updateAt(DependencyInfo.Type.Dynamic(symbol), dependents)
    }

    /** Combines two dependency information containers.
      *
      * @param that the other contaoner to combine with `this`
      * @return the result of combining `this` and `that`
      */
    def ++(that: DependencyInfo): DependencyInfo = {
      val combinedModule = new DependencyInfo(this.dependencies)

      for ((key, value) <- that.dependencies) {
        combinedModule.dependencies.get(key) match {
          case Some(xs) => combinedModule(key) = value ++ xs
          case None     => combinedModule(key) = value
        }
      }

      combinedModule
    }
  }
  object DependencyInfo {

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
      sealed case class Static(id: DependencyInfo.Identifier) extends Type

      /** Program components identified by their symbol.
        *
        * @param name the name of the symbol
        */
      sealed case class Dynamic(name: DependencyInfo.Symbol) extends Type
    }
  }
}
