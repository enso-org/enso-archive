package org.enso.compiler.pass.analyse

import java.util.UUID

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

import scala.collection.mutable

/** This pass implements dataflow analysis for Enso.
  *
  * Dataflow analysis is the processes of determining the dependencies between
  * program expressions.
  *
  * This pass needs to be run after [[AliasAnalysis]], [[DemandAnalysis]], and
  * [[TailCall]]. It also assumes that all members of [[IR.IRKind.Primitive]]
  * have been removed from the IR by the time it runs. This means that it _must_
  * run after all desugaring passes.
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

  /** Performs dataflow analysis on an inline expression.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val localScope = inlineContext.localScope.getOrElse(
      throw new CompilerError(
        "A valid local scope is required for the inline flow."
      )
    )
    analyseExpression(ir, localScope.dataflowInfo)
  }

  // === Pass Internals =======================================================

  def analyseModuleDefinition(
    binding: IR.Module.Scope.Definition,
    dependencyInfo: DependencyInfo
  ): IR.Module.Scope.Definition = {
    binding match {
      case atom @ IR.Module.Scope.Definition.Atom(_, arguments, _, _) =>
        arguments.foreach(arg =>
          dependencyInfo.updateAt(arg.getId, Set(atom.getId))
        )

        atom
          .copy(
            arguments =
              arguments.map(analyseDefinitionArgument(_, dependencyInfo))
          )
          .addMetadata(dependencyInfo)
      case m @ IR.Module.Scope.Definition.Method(_, _, body, _, _) =>
        dependencyInfo.updateAt(body.getId, Set(m.getId))

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
    expression match {
      case function: IR.Function => analyseFunction(function, dependencyInfo)
      case app: IR.Application   => analyseApplication(app, dependencyInfo)
      case typ: IR.Type          => analyseType(typ, dependencyInfo)
      case name: IR.Name         => analyseName(name, dependencyInfo)
      case cse: IR.Case          => analyseCase(cse, dependencyInfo)
      case comment: IR.Comment   => analyseComment(comment, dependencyInfo)
      case literal: IR.Literal   => literal.addMetadata(dependencyInfo)
      case foreign: IR.Foreign   => foreign.addMetadata(dependencyInfo)

      case block @ IR.Expression.Block(expressions, returnValue, _, _, _) =>
        dependencyInfo.updateAt(returnValue.getId, Set(block.getId))

        block
          .copy(
            expressions = expressions.map(analyseExpression(_, dependencyInfo)),
            returnValue = analyseExpression(returnValue, dependencyInfo)
          )
          .addMetadata(dependencyInfo)
      case binding @ IR.Expression.Binding(_, expression, _, _) =>
        dependencyInfo.updateAt(expression.getId, Set(binding.getId))

        binding
          .copy(
            expression = analyseExpression(expression, dependencyInfo)
          )
          .addMetadata(dependencyInfo)

      case error: IR.Error => error
    }
  }

  def analyseFunction(
    function: IR.Function,
    info: DependencyInfo
  ): IR.Function = {
    function match {
      case lam @ IR.Function.Lambda(arguments, body, _, _, _) =>
        info.updateAt(body.getId, Set(lam.getId))

        lam
          .copy(
            arguments = arguments.map(analyseDefinitionArgument(_, info)),
            body      = analyseExpression(body, info)
          )
          .addMetadata(info)
    }
  }

  def analyseApplication(
    application: IR.Application,
    info: DependencyInfo
  ): IR.Application = {
    application match {
      case prefix @ IR.Application.Prefix(fn, args, _, _, _) =>
        info.updateAt(fn.getId, Set(prefix.getId))
        args.foreach(arg => info.updateAt(arg.getId, Set(prefix.getId)))

        prefix
          .copy(
            function  = analyseExpression(fn, info),
            arguments = args.map(analyseCallArgument(_, info))
          )
          .addMetadata(info)
      case force @ IR.Application.Force(target, _, _) =>
        info.updateAt(target.getId, Set(force.getId))

        force.copy(target = analyseExpression(target, info)).addMetadata(info)
      case _: IR.Application.Operator =>
        throw new CompilerError("Unexpected operator during Dataflow Analysis.")
    }
  }

  def analyseType(typ: IR.Type, info: DependencyInfo): IR.Type = {
    typ match {
      case asc @ IR.Type.Ascription(typed, signature, _, _) =>
        info.updateAt(typed.getId, Set(asc.getId))
        info.updateAt(signature.getId, Set(asc.getId))

        asc
          .copy(
            typed     = analyseExpression(typed, info),
            signature = analyseExpression(signature, info)
          )
          .addMetadata(info)
      case ctx @ IR.Type.Context(typed, context, _, _) =>
        info.updateAt(typed.getId, Set(ctx.getId))
        info.updateAt(context.getId, Set(ctx.getId))

        ctx
          .copy(
            typed   = analyseExpression(typed, info),
            context = analyseExpression(context, info)
          )
          .addMetadata(info)
      case member @ IR.Type.Set.Member(_, memberType, value, _, _) =>
        info.updateAt(memberType.getId, Set(member.getId))
        info.updateAt(value.getId, Set(member.getId))

        member
          .copy(
            memberType = analyseExpression(memberType, info),
            value      = analyseExpression(value, info)
          )
          .addMetadata(info)
      case concat @ IR.Type.Set.Concat(left, right, _, _) =>
        info.updateAt(left.getId, Set(concat.getId))
        info.updateAt(right.getId, Set(concat.getId))

        concat
          .copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
      case eq @ IR.Type.Set.Equality(left, right, _, _) =>
        info.updateAt(left.getId, Set(eq.getId))
        info.updateAt(right.getId, Set(eq.getId))

        eq.copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
      case intersect @ IR.Type.Set.Intersection(left, right, _, _) =>
        info.updateAt(left.getId, Set(intersect.getId))
        info.updateAt(right.getId, Set(intersect.getId))

        intersect
          .copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
      case union @ IR.Type.Set.Union(left, right, _, _) =>
        info.updateAt(left.getId, Set(union.getId))
        info.updateAt(right.getId, Set(union.getId))

        union
          .copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
      case subsumption @ IR.Type.Set.Subsumption(left, right, _, _) =>
        info.updateAt(left.getId, Set(subsumption.getId))
        info.updateAt(right.getId, Set(subsumption.getId))

        subsumption
          .copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
      case subtraction @ IR.Type.Set.Subtraction(left, right, _, _) =>
        info.updateAt(left.getId, Set(subtraction.getId))
        info.updateAt(right.getId, Set(subtraction.getId))

        subtraction
          .copy(
            left  = analyseExpression(left, info),
            right = analyseExpression(right, info)
          )
          .addMetadata(info)
    }
  }

  def analyseName(name: IR.Name, info: DependencyInfo): IR.Name = {
    val aliasInfo = name.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
      "Name occurrence with missing aliasing information."
    )
    val defIdForName = aliasInfo.graph.defLinkFor(aliasInfo.id)

    val key = defIdForName match {
      case Some(defLink) =>
        aliasInfo.graph.getOccurrence(defLink.target) match {
          case Some(AliasAnalysis.Graph.Occurrence.Def(_, _, id, _)) =>
            DependencyInfo.Type.Static(id)
          case _ => DependencyInfo.Type.Dynamic(name.name)
        }

      case None => DependencyInfo.Type.Dynamic(name.name)
    }

    info.updateAt(key, Set(name.getId))

    name.addMetadata(info)
  }

  def analyseCase(cse: IR.Case, info: DependencyInfo): IR.Case = {
    cse match {
      case expr @ IR.Case.Expr(scrutinee, branches, fallback, _, _) =>
        info.updateAt(scrutinee.getId, Set(expr.getId))
        branches.foreach(branch => info.updateAt(branch.getId, Set(expr.getId)))
        fallback.foreach(fback => info.updateAt(fback.getId, Set(expr.getId)))

        expr
          .copy(
            scrutinee = analyseExpression(scrutinee, info),
            branches  = branches.map(analyseCaseBranch(_, info)),
            fallback  = fallback.map(analyseExpression(_, info))
          )
          .addMetadata(info)
      case _: IR.Case.Branch =>
        throw new CompilerError("Unexpected case branch.")
    }
  }

  def analyseCaseBranch(
    branch: IR.Case.Branch,
    info: DependencyInfo
  ): IR.Case.Branch = {
    val pattern    = branch.pattern
    val expression = branch.expression

    info.updateAt(pattern.getId, Set(branch.getId))
    info.updateAt(expression.getId, Set(branch.getId))

    branch
      .copy(
        pattern    = analyseExpression(pattern, info),
        expression = analyseExpression(expression, info)
      )
      .addMetadata(info)
  }

  def analyseComment(comment: IR.Comment, info: DependencyInfo): IR.Comment = {
    comment match {
      case doc @ IR.Comment.Documentation(commented, _, _, _) =>
        info.updateAt(commented.getId, Set(comment.getId))

        doc
          .copy(
            commented = analyseExpression(commented, info)
          )
          .addMetadata(info)
    }
  }

  def analyseDefinitionArgument(
    argument: IR.DefinitionArgument,
    dependencyInfo: DependencyInfo
  ): IR.DefinitionArgument = {
    argument match {
      case spec @ IR.DefinitionArgument.Specified(_, defValue, _, _, _) =>
        defValue.foreach(expr =>
          dependencyInfo.updateAt(expr.getId, Set(spec.getId))
        )

        spec
          .copy(
            defaultValue = defValue.map(analyseExpression(_, dependencyInfo))
          )
          .addMetadata(dependencyInfo)
      case err: IR.Error.Redefined.Argument => err
    }
  }

  def analyseCallArgument(
    argument: IR.CallArgument,
    dependencyInfo: DependencyInfo
  ): IR.CallArgument = {
    argument match {
      case spec @ IR.CallArgument.Specified(_, value, _, _, _) =>
        dependencyInfo.updateAt(value.getId, Set(spec.getId))

        spec
          .copy(
            value = analyseExpression(value, dependencyInfo)
          )
          .addMetadata(dependencyInfo)
    }
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
    val id                            = UUID.randomUUID()
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

    /** Obtains the _direct_ dependents of a given node in the IR.
      *
      * Please note that this does _not_ return the transitive closure of all
      * dependents of the node.
      *
      * @param key the key to get the dependents of
      * @return the set of the _direct_ dependencies on `key`, if it exists
      */
    def getDirect(
      key: DependencyInfo.Type
    ): Option[Set[DependencyInfo.Type]] = {
      dependencies.get(key)
    }

    /** Safely gets the set of all dependents for the provided key.
      *
      * Please note that the result set contains not just the _direct_
      * dependents of the key, but _all_ dependents of the key.
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

    /** Updates the dependents for the provided keys, or creates them if they do
      * not already exist.
      *
      * @param keys the keys to add or update dependents for
      * @param dependents the new dependents information for each `key` in
      *                   `keys`
      */
    def updateAt(
      keys: List[DependencyInfo.Type],
      dependents: Set[DependencyInfo.Type]
    ): Unit = keys.foreach(key => updateAt(key, dependents))

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

      // === Conversions ======================================================

      /** An implicit conversion from [[DependencyInfo.Identifier]] to a
        * [[Static]] dependency key.
        *
        * @param id the identifier
        * @return the [[Type]] wrapping `id`
        */
      implicit def idToStatic(id: DependencyInfo.Identifier): Static = {
        Static(id)
      }

      /** An implicit conversion from [[DependencyInfo.Symbol]] to a [[Dynamic]]
        * dependency key.
        *
        * @param symbol the symbol
        * @return the [[Type]] wrapping `symbol`
        */
      implicit def symbolToDynamic(symbol: DependencyInfo.Symbol): Dynamic = {
        Dynamic(symbol)
      }
    }
  }
}
