package org.enso.compiler.pass.optimise

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
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
        new InlineContext(passConfiguration = moduleContext.passConfiguration)
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
  ): IR.Expression = ir.transformExpressions {
    case fn: IR.Function => collapseFunction(fn, inlineContext)
  }

  /** Collapses chained lambdas for a function definition where possible.
    *
    * @param function the function definition to optimise
    * @return the optimised version of `function`, with any directly chained
    *         lambdas collapsed
    */
  def collapseFunction(
    function: IR.Function,
    inlineContext: InlineContext
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

        val shadows = chainedArgList.zipWithIndex.map {
          case (spec: IR.DefinitionArgument.Specified, ix) =>
            val aliasInfo =
              spec.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
                "Lambda argument definition missing aliasing information."
              )
            val aliasGraph: AliasAnalysis.Graph = aliasInfo.graph
            val shadowedIds = if (aliasGraph.shadows(aliasInfo.id)) {

            } else {
              List()
            }

            false
          case (_: IR.Error.Redefined.Argument, _) => false
        }

        // TODO [AA] This should use the alias analysis info, not do it ad hoc
        val argIsShadowed: List[Boolean] =
          for ((arg, ix) <- chainedArgList.zipWithIndex) yield {
            val remainingList = chainedArgList.drop(ix + 1)

            remainingList.exists {
              case spec: IR.DefinitionArgument.Specified =>
                arg match {
                  case arg: IR.DefinitionArgument.Specified =>
                    arg.name.name == spec.name.name
                  case _ => throw new RuntimeException
                }
              case _ => throw new RuntimeException
            }
          }

        val processedArgList = renameShadowedArguments(
          chainedArgList.zip(argIsShadowed)
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
    args: List[(IR.DefinitionArgument, Boolean)]
  ): List[IR.DefinitionArgument] = {
    // TODO [AA]
    args.map(_._1)
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
