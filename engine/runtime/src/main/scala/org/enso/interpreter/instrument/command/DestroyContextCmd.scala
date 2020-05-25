package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

class DestroyContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.DestroyContextRequest
) extends Command
    with EnsoExecutionSupport {

  override def execute(implicit ctx: RuntimeContext): Unit = {
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
          .Response(maybeRequestId, Api.ContextNotExistError(request.contextId))
      )
    }
  }

}
