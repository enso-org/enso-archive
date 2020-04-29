package org.enso.compiler.pass.optimise

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.syntax.text.Location

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
    // TODO [AA] Implement the pass
    // TODO [AA] Introduce a warning if a lambda chain shadows a var
    // TODO [AA] Account for defaults
    // TODO [AA] make sure to recurse through the body of the eventual function
    //  and the argument defaults

    function match {
      case lam @ IR.Function.Lambda(_, body, _, _, _) =>
        val chainedLambdas = lam :: gatherChainedLambdas(body)
        val chainedArgList =
          chainedLambdas.foldLeft(List[IR.DefinitionArgument]())((xs, lam) =>
            xs ::: lam.arguments
          )

        // TODO [AA] Need to compute if a binding is shadowed by another in the
        //  list
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

        val isShadowed = chainedArgList.map {
          case spec: IR.DefinitionArgument.Specified =>
            val aliasInfo =
              spec.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
                "Missing aliasing information for an argument definition."
              )
            shadowedBindingIds.contains(aliasInfo.id)
          case _: IR.Error.Redefined.Argument => false
        }

        val processedArgList = renameShadowedArguments(
          chainedArgList.zip(isShadowed),
          freshNameSupply
        )

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
          body      = runExpression(chainedLambdas.last.body, inlineContext),
          location  = newLocation,
          canBeTCO  = chainedLambdas.last.canBeTCO,
          passData  = Set()
        )
    }
  }

  /** Renames any arguments that have been shadowed by another argument.
    *
    * @param args the consolidated arguments list
    * @return `args`, with any shadowed arguments renamed
    */
  def renameShadowedArguments(
    args: List[(IR.DefinitionArgument, Boolean)],
    freshNameSupply: FreshNameSupply
  ): List[IR.DefinitionArgument] = {
    args.map {
      case (
          spec @ IR.DefinitionArgument.Specified(name, default, _, _, _),
          isShadowed
          ) =>
        // TODO [AA] Deal with usages of the argument
        if (isShadowed) {
          val newName = freshNameSupply.newName()

          spec.copy(
            name = newName.copy(location = name.location, id = name.getId)
          )
        } else {
          spec
        }
      case (e: IR.Error.Redefined.Argument, _) => e
    }
  }

  def replaceUsage(
    expr: IR.Expression,
    ident: IR.Identifier,
    newName: String
  ): IR.Expression = {
    ???
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
}
