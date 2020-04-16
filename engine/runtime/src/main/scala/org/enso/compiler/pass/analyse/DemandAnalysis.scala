package org.enso.compiler.pass.analyse

import org.enso.compiler.InlineContext
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

// TODO [AA] Need to remove the syntax for explicit forces.
/** This pass implements demand analysis for Enso.
  *
  * Demand analysis is the process of determining _when_ a suspended term needs
  * to be forced (where the suspended value is _demanded_). It does the
  * following:
  *
  * This pass needs to be run after [[AliasAnalysis]], and also assumes that
  * all members of [[IR.IRKind.Primitive]] have been removed from the IR by the
  * time that it runs.
  */
case object DemandAnalysis extends IRPass {
  override type Metadata = IR.Metadata.Empty

  /** Executes the demand analysis process on an Enso module.
    *
    * @param ir the Enso IR to process
    * @return `ir`, transformed to correctly force terms
    */
  override def runModule(ir: IR.Module): IR.Module = {
    ir.copy(bindings =
      ir.bindings.map(t => t.mapExpressions(runExpression(_, InlineContext())))
    )
  }

  /** Executes the demand analysis process on an Enso expression.
    *
    * @param expression the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, transformed to correctly force terms
    */
  override def runExpression(
    expression: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression =
    analyseExpression(
      expression,
      isInsideApplication  = false,
      isInsideCallArgument = false
    )

  def analyseExpression(
    expression: IR.Expression,
    isInsideApplication: Boolean, // actually _inside_
    isInsideCallArgument: Boolean
  ): IR.Expression = {
    expression match {
      case fn: IR.Function => analyseFunction(fn, isInsideApplication)
      case name: IR.Name =>
        analyseName(name, isInsideApplication, isInsideCallArgument)
      case app: IR.Application =>
        analyseApplication(app, isInsideApplication, isInsideCallArgument)
      case typ: IR.Type =>
        analyseType(typ, isInsideApplication, isInsideCallArgument)
      case cse: IR.Case =>
        analyseCase(cse, isInsideApplication, isInsideCallArgument)
      case block @ IR.Expression.Block(expressions, retVal, _, _, _) =>
        block.copy(
          expressions = expressions.map(x =>
            analyseExpression(x, isInsideApplication, isInsideCallArgument)
          ),
          returnValue =
            analyseExpression(retVal, isInsideApplication, isInsideCallArgument)
        )
      case binding @ IR.Expression.Binding(_, expression, _, _) =>
        binding.copy(expression =
          analyseExpression(
            expression,
            isInsideApplication,
            isInsideCallArgument = false
          )
        )
      case lit: IR.Literal     => lit
      case err: IR.Error       => err
      case foreign: IR.Foreign => foreign
      case comment: IR.Comment =>
        comment.mapExpressions(x =>
          analyseExpression(
            x,
            isInsideApplication = false,
            isInsideCallArgument
          )
        )
    }
  }

  def analyseFunction(
    function: IR.Function,
    isInsideApplication: Boolean
  ): IR.Function = function match {
    case lam @ IR.Function.Lambda(args, body, _, _, _) =>
      lam.copy(
        arguments = args.map(analyseDefinitionArgument),
        body = analyseExpression(
          body,
          isInsideApplication,
          isInsideCallArgument = false
        )
      )
  }

  def analyseName(
    name: IR.Name,
    isInsideApplication: Boolean,
    isInsideCallArgument: Boolean
  ): IR.Expression = {
    val usesLazyTerm = isUsageOfLazy(name)

    if (isInsideCallArgument) {
      name
    } else {
      if (usesLazyTerm) {
        IR.Application.Force(name, name.location)
      } else {
        name
      }
    }
  }

  def analyseApplication(
    application: IR.Application,
    isInsideApplication: Boolean,
    isInsideCallArgument: Boolean
  ): IR.Application = application match {
    case pref @ IR.Application.Prefix(fn, args, _, _, _) =>
      pref.copy(
        function = analyseExpression(
          fn,
          isInsideApplication  = true,
          isInsideCallArgument = false
        ),
        arguments = args.map(analyseCallArgument)
      )
    case force @ IR.Application.Force(target, _, _) =>
      force.copy(target =
        analyseExpression(
          target,
          isInsideApplication,
          isInsideCallArgument
        )
      )
    case _ =>
      throw new CompilerError(
        "Unexpected application type during demand analysis."
      )
  }

  def isUsageOfLazy(expr: IR.Expression): Boolean = {
    expr match {
      case name: IR.Name =>
        val aliasInfo = name.unsafeGetMetadata[AliasAnalysis.Info.Occurrence](
          "Missing alias occurrence information for a name usage"
        )

        aliasInfo.graph
          .defLinkFor(aliasInfo.id)
          .flatMap(link => {
            aliasInfo.graph
              .getOccurrence(link.target)
              .getOrElse(
                throw new CompilerError(
                  s"Malformed aliasing link with target ${link.target}"
                )
              ) match {
              case AliasAnalysis.Graph.Occurrence.Def(_, _, isLazy) =>
                if (isLazy) Some(true) else None
              case _ => None
            }
          })
          .isDefined
      case _ => false
    }
  }

  def analyseCallArgument(arg: IR.CallArgument): IR.CallArgument = {
    arg match {
      case spec @ IR.CallArgument.Specified(_, expr, _, _, _) =>
        spec.copy(
          value = analyseExpression(
            expr,
            isInsideApplication  = true,
            isInsideCallArgument = true
          ),
          shouldBeSuspended = Some(!isUsageOfLazy(expr))
        )
    }
  }

  def analyseDefinitionArgument(
    arg: IR.DefinitionArgument
  ): IR.DefinitionArgument = {
    arg match {
      case spec @ IR.DefinitionArgument.Specified(_, default, _, _, _) =>
        spec.copy(
          defaultValue = default.map(x =>
            analyseExpression(
              x,
              isInsideApplication  = false,
              isInsideCallArgument = false
            )
          )
        )
      case redef: IR.Error.Redefined.Argument => redef
    }
  }

  def analyseType(
    typ: IR.Type,
    isInsideApplication: Boolean,
    isInsideCallArgument: Boolean
  ): IR.Type =
    typ.mapExpressions(x =>
      analyseExpression(x, isInsideApplication, isInsideCallArgument)
    )

  def analyseCase(
    cse: IR.Case,
    isInsideApplication: Boolean,
    isInsideCallArgument: Boolean
  ): IR.Case = cse match {
    case expr @ IR.Case.Expr(scrutinee, branches, fallback, _, _) =>
      expr.copy(
        scrutinee = analyseExpression(
          scrutinee,
          isInsideApplication,
          isInsideCallArgument
        ),
        branches = branches.map(b => analyseCaseBranch(b)),
        fallback = fallback.map(x =>
          analyseExpression(
            x,
            isInsideApplication  = false,
            isInsideCallArgument = false
          )
        )
      )
    case _ => throw new CompilerError("Unexpected case construct.")
  }

  def analyseCaseBranch(branch: IR.Case.Branch): IR.Case.Branch = {
    branch.copy(
      expression = analyseExpression(
        branch.expression,
        isInsideApplication  = false,
        isInsideCallArgument = false
      )
    )
  }
}
