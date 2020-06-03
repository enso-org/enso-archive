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
  * A command that attaches a visualisation to an expression.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class AttachVisualisationCmd(
  maybeRequestId: Option[RequestId],
  request: Api.AttachVisualisation
) extends Command(maybeRequestId) {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] = {
    if (ctx.contextManager.contains(
          request.visualisationConfig.executionContextId
        )) {
      val maybeFutureExecutable =
        ctx.jobProcessor.run(
          new UpsertVisualisationJob(
            maybeRequestId,
            request.visualisationId,
            request.expressionId,
            request.visualisationConfig,
            Api.VisualisationAttached()
          )
        )

      maybeFutureExecutable flatMap {
        case None =>
          Future.successful(())

        case Some(executable) =>
          for {
            _ <- ctx.jobProcessor.run(new EnsureCompiledJob(executable.stack))
            _ <- ctx.jobProcessor.run(new ExecuteJob(executable))
          } yield ()
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
