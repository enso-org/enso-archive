package org.enso.compiler.pass.desugar

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Error.Redefined
import org.enso.compiler.core.IR.Module.Scope.Definition
import org.enso.compiler.core.IR.Module.Scope.Definition.Method
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass

import scala.annotation.unused

/** This pass handles the desugaring of long-form function and method
  * definitions into standard bindings using lambdas.
  *
  * This works for any definition of the form `f <args> = <body>`.
  *
  * This pass has no configuration.
  *
  * This pass requires the context to provide:
  *
  * - Nothing
  *
  * It must have the following passes run before it:
  *
  * - None
  */
case object FunctionBinding extends IRPass {
  override type Metadata = IRPass.Metadata.Empty
  override type Config   = IRPass.Configuration.Default

  /** Rusn desugaring of sugared method and function bindings on a module.
    *
    * @param ir the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(
    ir: IR.Module,
    @unused moduleContext: ModuleContext
  ): IR.Module = ir.copy(bindings = ir.bindings.map(desugarModuleSymbol))

  /** Runs desugaring of function bindings on an arbitrary expression.
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
    case IR.Function.Binding(name, args, body, location, canBeTCO, _, _) =>
      if (args.isEmpty) {
        throw new CompilerError("The arguments list should not be empty.")
      }

      val lambda = args
        .map(_.mapExpressions(runExpression(_, inlineContext)))
        .foldRight(runExpression(body, inlineContext))((arg, body) =>
          IR.Function.Lambda(List(arg), body, None)
        )
        .asInstanceOf[IR.Function.Lambda]
        .copy(canBeTCO = canBeTCO)

      IR.Expression.Binding(name, lambda, location)
  }

  // === Pass Internals =======================================================

  /** Performs desugaring on a module definition.
    *
    * @param definition the module definition to desugar
    * @return `definition`, with any function definition sugar removed
    */
  def desugarModuleSymbol(
    definition: IR.Module.Scope.Definition
  ): IR.Module.Scope.Definition = {
    definition match {
      case a @ Definition.Atom(_, arguments, _, _, _) =>
        a.copy(
          arguments =
            arguments.map(_.mapExpressions(runExpression(_, InlineContext())))
        )
      case method @ Method.Explicit(_, _, body, _, _, _) =>
        method.copy(
          body = runExpression(body, InlineContext())
        )
      case Method.Binding(typeName, methName, args, body, loc, _, _) =>
        val newBody = args
          .map(_.mapExpressions(runExpression(_, InlineContext())))
          .foldRight(runExpression(body, InlineContext()))((arg, body) =>
            IR.Function.Lambda(List(arg), body, None)
          )
          .asInstanceOf[IR.Function.Lambda]
          .copy(location = loc, canBeTCO = true)

        Method.Explicit(typeName, methName, newBody, None)
      case e: Redefined => e
    }
  }
}
