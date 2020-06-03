package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.{
  EnsureCompiledJob,
  ExecuteJob,
  UpsertVisualisationJob
}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that modifies a visualisation.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class ModifyVisualisationCmd(
  maybeRequestId: Option[RequestId],
  request: Api.ModifyVisualisation
) extends Command {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] = {
    if (ctx.contextManager.contains(
          request.visualisationConfig.executionContextId
        )) {
      val maybeVisualisation = ctx.contextManager.getVisualisationById(
        request.visualisationConfig.executionContextId,
        request.visualisationId
      )
      maybeVisualisation match {
        case None =>
          Future {
            ctx.endpoint.sendToClient(
              Api.Response(maybeRequestId, Api.VisualisationNotFound())
            )
          }

        case Some(visualisation) =>
          val maybeExeFuture =
            ctx.jobProcessor.run(
              new UpsertVisualisationJob(
                maybeRequestId,
                request.visualisationId,
                visualisation.expressionId,
                request.visualisationConfig,
                Api.VisualisationModified()
              )
            )
          maybeExeFuture flatMap {
            case None =>
              Future.successful(())

            case Some(executable) =>
              for {
                _ <- ctx.jobProcessor.run(
                  new EnsureCompiledJob(executable.stack)
                )
                _ <- ctx.jobProcessor.run(new ExecuteJob(executable))
              } yield ()
          }
      }

    } else {
      replyWithContextNotExistError()
    }
  }

  private def replyWithContextNotExistError()(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] = {
    Future {
      ctx.endpoint.sendToClient(
        Api.Response(
          maybeRequestId,
          Api.ContextNotExistError(
            request.visualisationConfig.executionContextId
          )
        )
      )
    }
  }

}
