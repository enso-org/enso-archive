package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Application.Operator.Section
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

/** This pass converts operator sections to applications of binary operators.
  *
  * It depends on [[GenerateMethodBodies]] to run.
  */
case object SectionsToBinOp extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Performs section to binary operator conversion on an IR module.
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
        new InlineContext(freshNameSupply = moduleContext.freshNameSupply)
      )
  }

  /** Performs section to binary operator conversion on an IR expression.
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
        "A fresh name supply is required for sections desugaring."
      )
    )

    ir.transformExpressions {
      case sec: IR.Application.Operator.Section =>
        desugarSections(sec, freshNameSupply)
    }
  }

  /** Desugars operator sections to fully-saturated applications of operators.
    *
    * For a left and sides sections it will generate a partially-applied
    * function. For right sections it will generate a lambda.
    *
    * @param section the section to desugar
    * @return the result of desugaring `section`
    */
  def desugarSections(
    section: IR.Application.Operator.Section,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    section match {
      case Section.Left(arg, op, loc, passData, diagnostics) =>
        IR.Application.Prefix(
          op,
          List(arg),
          hasDefaultsSuspended = false,
          loc,
          passData,
          diagnostics
        )
      case Section.Sides(op, loc, passData, diagnostics) =>
        IR.Application.Prefix(
          op,
          List(),
          hasDefaultsSuspended = false,
          loc,
          passData,
          diagnostics
        )
      case Section.Right(op, arg, loc, passData, diagnostics) =>
        val leftArgName = freshNameSupply.newName()
        val leftCallArg =
          IR.CallArgument.Specified(None, leftArgName, None, None)
        val leftDefArg =
          IR.DefinitionArgument.Specified(
            // Ensure it has a different identifier
            leftArgName.copy(id = IR.randomId),
            None,
            suspended = false,
            None
          )

        val opCall = IR.Application.Prefix(
          op,
          List(leftCallArg, arg),
          hasDefaultsSuspended = false,
          loc,
          passData,
          diagnostics
        )

        IR.Function.Lambda(
          List(leftDefArg),
          opCall,
          loc,
          canBeTCO = true,
          passData,
          diagnostics
        )
    }
  }
}
