package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** This pass translates `_` arguments at application sites to lambda functions.
  *
  * It requires [[GenerateMethodBodies]], [[SectionsToBinOp]] and
  * [[OperatorToFunction]] to have run before it.
  */
case object UnderscoreToLambda extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Desugars underscore arguments to lambdas for a module.
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
      x.mapExpressions(
        runExpression(
          _,
          InlineContext(freshNameSupply = moduleContext.freshNameSupply)
        )
      )
  }

  /** Desugars underscore arguments to lambdas for an arbitrary expression.
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
        "Desugaring underscore arguments to lambdas requires a fresh name " +
        "supply."
      )
    )

    desugarExpression(ir, freshNameSupply)
  }

  /** Performs lambda shorthand desugaring on an arbitrary expression.
    *
    * @param ir the expression to desugar
    * @param freshNameSupply the compiler's fresh name supply
    * @return `ir`, with any lambda shorthand arguments desugared
    */
  def desugarExpression(
    ir: IR.Expression,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    ir.transformExpressions {
      case app: IR.Application    => desugarApplication(app, freshNameSupply)
      case caseExpr: IR.Case.Expr => desugarCaseExpr(caseExpr, freshNameSupply)
    }
  }

  /** Desugars lambda shorthand arguments to an arbitrary function application.
    *
    * @param application the function application to desugar
    * @param freshNameSupply the compiler's supply of fresh names
    * @return `application`, with any lambda shorthand arguments desugared
    */
  def desugarApplication(
    application: IR.Application,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    application match {
      case p @ IR.Application.Prefix(_, args, _, _, _, _) =>
        // TODO [AA] underscores can occur in the function position or the arg
        //  positions
        // TODO [AA] Be super careful about order (a _ _ c) needs to maintain
        //  the order of lam args (even in the named case)
        // TODO [AA] I can (and should) generate single arg lambdas here.

        // Determine which arguments are lambda shorthand
        val argIsUnderscore = determineLambdaShorthand(args)

        // TODO 2. For each underscore generate a new name instance, and replace
        //  the arg value with it
        @unused val updatedArgs =
          args.zip(argIsUnderscore).map(updateShorthandArg(_, freshNameSupply))

        // TODO 3. Generate a definition arg instance for each call arg that
        //  was an underscore

        // TODO 4. Wrap the app in lambdas from right to left, one lambda per
        //  underscore arg

        // TODO 5. If the function is a lambda, then do the same for the
        //  function, wrapping it as the outermost arg

        p
      case f @ IR.Application.Force(tgt, _, _, _) =>
        f.copy(target = desugarExpression(tgt, freshNameSupply))
      case _: IR.Application.Operator =>
        throw new CompilerError(
          "Operators should be desugared by the point of underscore to " +
          "lambda conversion."
        )
    }
  }

  /** Determines, positionally, which of the application arguments are lambda
    * shorthand arguments.
    *
    * @param args the application arguments
    * @return a list containing `true` for a given position if the arg in that
    *         position is lambda shorthand, otherwise `false`
    */
  def determineLambdaShorthand(args: List[IR.CallArgument]): List[Boolean] = {
    args.map {
      case IR.CallArgument.Specified(_, value, _, _, _, _) =>
        value match {
          case _: IR.Expression.Blank => true
          case _                      => false
        }
    }
  }

  /** Generates a new name to replace a shorthand argument, as well as the
    * corresponding definition argument.
    *
    * @param argAndIsShorthand the arguments, and whether or not the argument in
    *                          the corresponding position is shorthand
    * @return the above described pair for a given position if the argument in
    *         a given position is shorthand, otherwise [[None]].
    */
  def updateShorthandArg(
    argAndIsShorthand: (IR.CallArgument, Boolean),
    freshNameSupply: FreshNameSupply
  ): IR.CallArgument = {
    val arg         = argAndIsShorthand._1
    val isShorthand = argAndIsShorthand._2

    arg match {
      case s @ IR.CallArgument.Specified(_, value, _, _, _, _) =>
        if (isShorthand) {
          val newName = freshNameSupply
            .newName()
            .copy(
              location    = value.location,
              passData    = value.passData,
              diagnostics = value.diagnostics
            )

          s.copy(value = newName)
        } else s
    }
  }

  /** Performs desugaring of lambda shorthand arguments in a case expression.
    *
    * In the case where a user writes `case _ of`, this gets converted into a
    * lambda (`x -> case x of`).
    *
    * @param caseExpr the case expression to desugar
    * @param freshNameSupply the compiler's supply of fresh names
    * @return `caseExpr`, with any lambda shorthand desugared
    */
  def desugarCaseExpr(
    caseExpr: IR.Case.Expr,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    val newBranches = caseExpr.branches.map(
      _.mapExpressions(expr => desugarExpression(expr, freshNameSupply))
    )
    val newFallback =
      caseExpr.fallback.map(desugarExpression(_, freshNameSupply))

    caseExpr.scrutinee match {
      case IR.Expression.Blank(loc, passData, diagnostics) =>
        val scrutineeName =
          freshNameSupply
            .newName()
            .copy(
              location    = loc,
              passData    = passData,
              diagnostics = diagnostics
            )

        val lambdaArg = IR.DefinitionArgument.Specified(
          scrutineeName.copy(id = IR.randomId),
          None,
          suspended = false,
          None
        )

        val newCaseExpr = caseExpr.copy(
          scrutinee = scrutineeName,
          branches  = newBranches,
          fallback  = newFallback
        )

        IR.Function.Lambda(
          List(lambdaArg),
          newCaseExpr,
          caseExpr.location,
          passData    = caseExpr.passData,
          diagnostics = caseExpr.diagnostics
        )
      case x =>
        caseExpr.copy(
          scrutinee = desugarExpression(x, freshNameSupply),
          branches  = newBranches,
          fallback  = newFallback
        )
    }
  }
}
