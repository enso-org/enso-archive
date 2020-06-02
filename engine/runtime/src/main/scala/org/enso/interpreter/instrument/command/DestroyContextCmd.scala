package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.ProgramExecutionSupport
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that destroys the specified execution context.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class DestroyContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.DestroyContextRequest
) extends Command
    with ProgramExecutionSupport {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    Future {
      if (ctx.contextManager.get(request.contextId).isDefined) {
        ctx.contextManager.destroy(request.contextId)
        ctx.endpoint.sendToClient(
          Api.Response(
            maybeRequestId,
            Api.DestroyContextResponse(request.contextId)
          )
        )
      } else {
        ctx.endpoint.sendToClient(
          Api
            .Response(
              maybeRequestId,
              Api.ContextNotExistError(request.contextId)
            )
        )
      }
    }

}
