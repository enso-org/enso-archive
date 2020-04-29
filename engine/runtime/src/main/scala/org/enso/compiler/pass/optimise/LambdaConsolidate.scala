package org.enso.compiler.pass.optimise

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.syntax.text.Location

// TODO [AA] Refactor the heck out of the massive function
/** This pass consolidates chains of lambdas into multi-argument lambdas
  * internally.
  *
  * Enso's syntax, due to its unified design, only supports single-argument
  * lambda expressions. However, internally, we want to be able to use
  * multi-argument lambda expressions for performance reasons. This pass turns
  * these chains of lambda expressions into multi-argument lambdas.
  *
  * That means that code like this:
  *
  * {{{
  *   x -> y -> z -> ...
  * }}}
  *
  * Is translated to an internal representation equivalent to
  *
  * {{{
  *   x y z -> ...
  * }}}
  *
  * It requires [[org.enso.compiler.pass.analyse.AliasAnalysis]] to be run
  * _directly_ before it.
  *
  * Please note that this pass invalidates _all_ metdata on the transformed
  * portions of the program, and hence must be run before the deeper analysis
  * passes.
  */
case object LambdaConsolidate extends IRPass {
  override type Metadata = IR.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Performs lambda consolidation on a module.
    *
    * @param ir the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = ir.transformExpressions {
    case x =>
      runExpression(
        x,
        new InlineContext(
          freshNameSupply   = moduleContext.freshNameSupply,
          passConfiguration = moduleContext.passConfiguration
        )
      )
  }

  /** Performs lambda consolidation on an expression.
    *
    * @param ir the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val freshNameSupply = inlineContext.freshNameSupply.getOrElse(
      throw new CompilerError(
        "A fresh name supply is required for lambda consolidation."
      )
    )
    ir.transformExpressions {
      case fn: IR.Function =>
        collapseFunction(fn, inlineContext, freshNameSupply)
    }
  }

  /** Collapses chained lambdas for a function definition where possible.
    *
    * @param function the function definition to optimise
    * @return the optimised version of `function`, with any directly chained
    *         lambdas collapsed
    */
  def collapseFunction(
    function: IR.Function,
    inlineContext: InlineContext,
    freshNameSupply: FreshNameSupply
  ): IR.Function = {
    // TODO [AA] Introduce a warning if a lambda chain shadows a var

    function match {
      case lam @ IR.Function.Lambda(_, body, _, _, _) =>
        val chainedLambdas = lam :: gatherChainedLambdas(body)
        val chainedArgList =
          chainedLambdas.foldLeft(List[IR.DefinitionArgument]())(
            _ ::: _.arguments
          )
        val lastBody = chainedLambdas.last.body

        // Compute the set of all argument definitions that are shadowed
        val shadowedBindingIds = chainedArgList
          .map {
            case spec: IR.DefinitionArgument.Specified =>
              val aliasInfo =
                spec.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
                  "Missing aliasing information for an argument definition."
                )
              aliasInfo.graph
                .getOccurrence(aliasInfo.id)
                .flatMap(occ =>
                  Some(aliasInfo.graph.knownShadowedDefinitions(occ))
                )
                .getOrElse(Set())
            case _: IR.Error.Redefined.Argument => Set()
          }
          .foldLeft(Set[AliasAnalysis.Graph.Occurrence]())(_ ++ _)
          .map(_.id)

        val argIsShadowed = chainedArgList.map {
          case spec: IR.DefinitionArgument.Specified =>
            val aliasInfo =
              spec.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
                "Missing aliasing information for an argument definition."
              )
            shadowedBindingIds.contains(aliasInfo.id)
          case _: IR.Error.Redefined.Argument => false
        }

        val argsWithShadowed = chainedArgList.zip(argIsShadowed)

        // Get the usage ids for shadowed arguments Map[Name, Set[ID]]
        val usageIdsForShadowed: List[Set[IR.Identifier]] =
          argsWithShadowed.map {
            case (spec: IR.DefinitionArgument.Specified, isShadowed) =>
              val aliasInfo =
                spec.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
                  "Function argument definition is missing aliasing information."
                )

              // Empty set is used to indicate that it isn't shadowed
              val usageIds =
                if (isShadowed) {
                  aliasInfo.graph
                    .linksFor(aliasInfo.id)
                    .filter(_.target == aliasInfo.id)
                    .map(link => aliasInfo.graph.getOccurrence(link.source))
                    .collect {
                      case Some(
                          AliasAnalysis.Graph.Occurrence.Use(_, _, identifier)
                          ) =>
                        identifier
                    }
                } else Set[IR.Identifier]()

              usageIds
            case (_: IR.Error.Redefined.Argument, _) => Set()
          }

        // Get the new names for the shadowed arguments List[IR.Name]
        val newNames: List[IR.DefinitionArgument] = argsWithShadowed.map {
          case (
              spec @ IR.DefinitionArgument.Specified(name, _, _, _, _),
              isShadowed
              ) =>
            val newName =
              if (isShadowed) {
                freshNameSupply
                  .newName()
                  .copy(
                    location = name.location,
                    passData = name.passData,
                    id       = name.getId
                  )
              } else name

            spec.copy(name = newName)
          case (e: IR.Error.Redefined.Argument, _) => e
        }

        var newBody     = lastBody
        var newDefaults = chainedArgList.map(_.defaultValue)

        // Filter to just the ones needing replacement
        val namesNeedingReplacement =
          newNames.zip(usageIdsForShadowed).filterNot(x => x._2.isEmpty)

        // Replace all occurrences in the defaults and the body
        for ((arg, idents) <- namesNeedingReplacement) {
          val (updatedBody, updatedDefaults) =
            replaceUsages(newBody, newDefaults, arg, idents)

          newBody     = updatedBody
          newDefaults = updatedDefaults
        }

        // Reconstruct the arguments from the defaults)
        val processedArgList = newNames.zip(newDefaults).map {
          case (spec: IR.DefinitionArgument.Specified, default) =>
            spec.copy(defaultValue = default)
          case (e: IR.Error.Redefined.Argument, _) => e
        }

        // Compute the new location
        val newLocation = chainedLambdas.head.location match {
          // TODO [MK] Marcin please check the handling of the location is
          //  correct, particularly the ID
          case Some(location) =>
            Some(
              IR.IdentifiedLocation(
                Location(
                  location.start,
                  chainedLambdas.last.location.getOrElse(location).location.end
                ),
                location.id
              )
            )
          case None => None
        }

        lam.copy(
          arguments = processedArgList,
          body      = runExpression(newBody, inlineContext),
          location  = newLocation,
          canBeTCO  = chainedLambdas.last.canBeTCO,
          passData  = Set()
        )
    }
  }

  /** Generates a list of all the lambdas directly chained in the provided
    * function body.
    *
    * @param body the function body to optimise
    * @return the directly chained lambdas in `body`
    */
  def gatherChainedLambdas(body: IR.Expression): List[IR.Function.Lambda] = {
    body match {
      case l @ IR.Function.Lambda(_, body, _, _, _) =>
        l :: gatherChainedLambdas(body)
      case _ => List()
    }
  }

  def replaceUsages(
    body: IR.Expression,
    defaults: List[Option[IR.Expression]],
    argument: IR.DefinitionArgument,
    toReplaceExpressionIds: Set[IR.Identifier]
  ): (IR.Expression, List[Option[IR.Expression]]) = {
    (
      replaceInExpression(body, argument, toReplaceExpressionIds),
      defaults.map(
        _.map(replaceInExpression(_, argument, toReplaceExpressionIds))
      )
    )
  }

  // Use the fact that usages can only be names
  def replaceInExpression(
    expr: IR.Expression,
    argument: IR.DefinitionArgument,
    toReplaceExpressionIds: Set[IR.Identifier]
  ): IR.Expression = {
    expr.transformExpressions {
      case name: IR.Name =>
        replaceInName(name, argument, toReplaceExpressionIds)
    }
  }

  def replaceInName(
    name: IR.Name,
    argument: IR.DefinitionArgument,
    toReplaceExpressionIds: Set[IR.Identifier]
  ): IR.Name = {
    if (toReplaceExpressionIds.contains(name.getId)) {
      name match {
        case spec: IR.Name.Literal =>
          spec.copy(
            name = argument match {
              case defSpec: IR.DefinitionArgument.Specified => defSpec.name.name
              case _: IR.Error.Redefined.Argument           => spec.name
            }
          )
        case ths: IR.Name.This  => ths
        case here: IR.Name.Here => here
      }
    } else {
      name
    }
  }
}
