package org.enso.compiler.pass.analyse

import java.util

import org.enso.compiler.exception.CompilerError
import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.ir.MetadataStorage._
import org.enso.compiler.pass.IRPass

/** This pass implements the preference analysis for caching.
  *
  * The pass assigns weights to the expressions. The greater the weight, the
  * more preferable the expression for caching.
  *
  * Weights:
  *
  *   - `1` - Right hand side expressions
  */
case object CachePreferenceAnalysis extends IRPass {

  override type Metadata = WeightInfo

  /** @inheritdoc **/
  override val precursorPasses: Seq[IRPass] = List(
    AliasAnalysis,
    DemandAnalysis,
    TailCall
  )

  /** @inheritdoc **/
  override val invalidatedPasses: Seq[IRPass] = List()

  /** @inheritdoc **/
  override def runModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = {
    val weights = WeightInfo()
    ir.copy(bindings = ir.bindings.map(analyseModuleDefinition(_, weights)))
      .updateMetadata(this -->> weights)
  }

  /** @inheritdoc **/
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val localScope = inlineContext.localScope.getOrElse(
      throw new CompilerError(
        "A valid local scope is required for the inline flow."
      )
    )
    analyseExpression(ir, localScope.cacheInfo)
  }

  // === Pass Internals =======================================================

  /** Performas preference analysis on a module definition
    *
    * @param binding the binding to perform dataflow analysis on
    * @param weights the weights information for the module
    * @return `binding`, with attached preference information
    */
  def analyseModuleDefinition(
    binding: IR.Module.Scope.Definition,
    weights: WeightInfo
  ): IR.Module.Scope.Definition =
    binding match {
      case atom @ IR.Module.Scope.Definition.Atom(_, arguments, _, _, _) =>
        atom
          .copy(arguments =
            arguments.map(analyseDefinitionArgument(_, weights))
          )
          .updateMetadata(this -->> weights)
      case method @ IR.Module.Scope.Definition.Method
            .Explicit(_, _, body, _, _, _) =>
        method
          .copy(body = analyseExpression(body, weights))
          .updateMetadata(this -->> weights)
      case _: IR.Module.Scope.Definition.Method.Binding =>
        throw new CompilerError(
          "Sugared method definitions should not occur during cache " +
          "preference analysis."
        )
      case _: IR.Module.Scope.Definition.Type =>
        throw new CompilerError(
          "Complex type definitions should not be present during cache " +
          "preference analysis."
        )
      case _: IR.Comment.Documentation =>
        throw new CompilerError(
          "Documentation should not exist as an entity during cacache " +
          "preference analysis."
        )
      case err: IR.Error => err
    }

  /** Performs preference analysis on an arbitrary expression.
    *
    * @param expression the expression to perform the analysis on
    * @param weights the weights information for the module
    * @return `expression`, with attached preference information
    */
  def analyseExpression(
    expression: IR.Expression,
    weights: WeightInfo
  ): IR.Expression = {
    expression match {
      case empty: IR.Empty       => empty.updateMetadata(this -->> weights)
      case function: IR.Function => analyseFunction(function, weights)
      case app: IR.Application   => analyseApplication(app, weights)
      case typ: IR.Type          => typ.updateMetadata(this -->> weights)
      case name: IR.Name         => name.updateMetadata(this -->> weights)
      case cse: IR.Case          => analyseCase(cse, weights)
      case _: IR.Comment =>
        throw new CompilerError(
          "Comments should not be present during cache preference analysis."
        )
      case literal: IR.Literal =>
        literal.updateMetadata(this -->> weights)
      case foreign: IR.Foreign =>
        foreign.updateMetadata(this -->> weights)

      case block @ IR.Expression.Block(expressions, returnValue, _, _, _, _) =>
        block
          .copy(
            expressions = expressions.map(analyseExpression(_, weights)),
            returnValue = analyseExpression(returnValue, weights)
          )
          .updateMetadata(this -->> weights)

      case binding @ IR.Expression.Binding(name, expression, _, _, _) =>
        expression.getExternalId.foreach(weights.update(_, 1L))
        binding
          .copy(
            name       = name.updateMetadata(this -->> weights),
            expression = analyseExpression(expression, weights)
          )
          .updateMetadata(this -->> weights)

      case error: IR.Error => error
    }
  }

  /** Performs preference analysis on a function.
    *
    * @param function the function to perform preference analysis on
    * @param weights the weights information for the module
    * @return `function`, with attached preference information
    */
  def analyseFunction(
    function: IR.Function,
    weights: WeightInfo
  ): IR.Function = {
    function match {
      case lam @ IR.Function.Lambda(arguments, body, _, _, _, _) =>
        lam
          .copy(
            arguments = arguments.map(analyseDefinitionArgument(_, weights)),
            body      = analyseExpression(body, weights)
          )
          .updateMetadata(this -->> weights)
      case _: IR.Function.Binding =>
        throw new CompilerError(
          "Function sugar should not be present during cache preference analysis."
        )
    }
  }

  /** Preforms preference analysis on an application.
    *
    * @param application the appliation to perform preference analysis on
    * @param weights the weights information for the module
    * @return `application`, with attached preference information
    */
  def analyseApplication(
    application: IR.Application,
    weights: WeightInfo
  ): IR.Application = {
    application match {
      case prefix @ IR.Application.Prefix(fn, args, _, _, _, _) =>
        prefix
          .copy(
            function  = analyseExpression(fn, weights),
            arguments = args.map(analyseCallArgument(_, weights))
          )
          .updateMetadata(this -->> weights)
      case force @ IR.Application.Force(target, _, _, _) =>
        force
          .copy(target = analyseExpression(target, weights))
          .updateMetadata(this -->> weights)
      case vector @ IR.Application.Literal.Sequence(items, _, _, _) =>
        vector
          .copy(items = items.map(analyseExpression(_, weights)))
          .updateMetadata(this -->> weights)
      case _: IR.Application.Operator =>
        throw new CompilerError(
          "Unexpected operator during cache preference analysis."
        )
    }
  }

  /** Preforms preference analysis on a case expression.
    *
    * @param cse the case expression to perform preference analysis on
    * @param weights the weights information for the module
    * @return `cse`, with attached preference information
    */
  def analyseCase(cse: IR.Case, weights: WeightInfo): IR.Case = {
    cse match {
      case expr @ IR.Case.Expr(scrutinee, branches, fallback, _, _, _) =>
        expr
          .copy(
            scrutinee = analyseExpression(scrutinee, weights),
            branches  = branches.map(analyseCaseBranch(_, weights)),
            fallback  = fallback.map(analyseExpression(_, weights))
          )
          .updateMetadata(this -->> weights)
      case _: IR.Case.Branch =>
        throw new CompilerError("Unexpected case branch.")
    }
  }

  /** Preforms preference analysis on a case branch.
    *
    * @param branch the case branch to perform preference analysis on
    * @param weights the weights information for the module
    * @return `branch`, with attached preference information
    */
  def analyseCaseBranch(
    branch: IR.Case.Branch,
    weights: WeightInfo
  ): IR.Case.Branch = {
    branch
      .copy(
        pattern    = analyseExpression(branch.pattern, weights),
        expression = analyseExpression(branch.expression, weights)
      )
      .updateMetadata(this -->> weights)
  }

  /** Performs preference analysis on a function definition argument.
    *
    * @param argument the definition argument to perform the analysis on
    * @param weights the weights information for the module
    * @return `argument`, with attached preference information
    */
  def analyseDefinitionArgument(
    argument: IR.DefinitionArgument,
    weights: WeightInfo
  ): IR.DefinitionArgument = {
    argument match {
      case spec @ IR.DefinitionArgument.Specified(_, defValue, _, _, _, _) =>
        spec
          .copy(defaultValue = defValue.map(analyseExpression(_, weights)))
          .updateMetadata(this -->> weights)
    }
  }

  /** Performs preference analysis on a function call argument.
    *
    * @param argument the call argument to perform the analysis on
    * @param weights the weights information for the module
    * @return `argument`, with attached preference information
    */
  def analyseCallArgument(
    argument: IR.CallArgument,
    weights: WeightInfo
  ): IR.CallArgument = {
    argument match {
      case spec @ IR.CallArgument.Specified(_, value, _, _, _, _) =>
        spec
          .copy(value = analyseExpression(value, weights))
          .updateMetadata(this -->> weights)
    }
  }

  // === Pass Metadata ========================================================

  case class WeightInfo(
    weights: util.HashMap[IR.ExternalId, java.lang.Long] = new util.HashMap()
  ) extends IRPass.Metadata {

    /** The name of the metadata as a string. */
    override val metadataName: String = "CachePreferenceAnalysis.Weights"

    def update(id: IR.ExternalId, weight: Long): Unit =
      weights.put(id, weight)

    def get(id: IR.ExternalId): Long =
      weights.getOrDefault(id, 0L)
  }
}
