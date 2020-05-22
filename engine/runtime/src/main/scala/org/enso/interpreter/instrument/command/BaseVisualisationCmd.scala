package org.enso.interpreter.instrument.command

import cats.implicits._
import org.enso.interpreter.instrument.Handler.{
  EvalFailure,
  EvaluationFailed,
  ModuleNotFound
}
import org.enso.interpreter.instrument.{RuntimeContext, Visualisation}
import org.enso.polyglot.runtime.Runtime.Api.{
  ExpressionId,
  RequestId,
  VisualisationId
}
import org.enso.polyglot.runtime.Runtime.{Api, ApiResponse}

import scala.util.control.NonFatal

abstract class BaseVisualisationCmd extends Command with EnsoExecutionSupport {

  protected def upsertVisualisation(
    requestId: Option[RequestId],
    visualisationId: VisualisationId,
    expressionId: ExpressionId,
    config: Api.VisualisationConfiguration,
    replyWith: ApiResponse
  )(implicit ctx: RuntimeContext): Unit = {
    val maybeCallable =
      evaluateExpression(config.visualisationModule, config.expression)

    maybeCallable match {
      case Left(ModuleNotFound) =>
        ctx.endpoint.sendToClient(
          Api.Response(
            requestId,
            Api.ModuleNotFound(config.visualisationModule)
          )
        )

      case Left(EvaluationFailed(msg)) =>
        ctx.endpoint.sendToClient(
          Api.Response(
            requestId,
            Api.VisualisationExpressionFailed(msg)
          )
        )

      case Right(callable) =>
        val visualisation = Visualisation(
          visualisationId,
          expressionId,
          callable
        )
        ctx.contextManager.upsertVisualisation(
          config.executionContextId,
          visualisation
        )
        ctx.endpoint.sendToClient(
          Api.Response(requestId, replyWith)
        )
        val stack = ctx.contextManager.getStack(config.executionContextId)
        withContext(runEnso(config.executionContextId, stack.toList))
    }
  }

  private def evaluateExpression(
    moduleName: String,
    expression: String
  )(implicit ctx: RuntimeContext): Either[EvalFailure, AnyRef] = {
    val maybeModule = ctx.executionService.findModule(moduleName)

    val notFoundOrModule =
      if (maybeModule.isPresent) Right(maybeModule.get())
      else Left(ModuleNotFound)

    notFoundOrModule.flatMap { module =>
      try {
        withContext {
          ctx.executionService.evaluateExpression(module, expression).asRight
        }
      } catch {
        case NonFatal(th) => EvaluationFailed(th.getMessage).asLeft
      }
    }

  }

}
