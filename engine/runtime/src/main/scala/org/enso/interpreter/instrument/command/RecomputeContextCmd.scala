package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

class RecomputeContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.RecomputeContextRequest
) extends Command
    with EnsoExecutionSupport {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    if (ctx.contextManager.get(request.contextId).isDefined) {
      val stack = ctx.contextManager.getStack(request.contextId)
      val payload = if (stack.isEmpty) {
        Api.EmptyStackError(request.contextId)
      } else {
        withContext(runEnso(request.contextId, stack.toList)) match {
          case Right(()) => Api.RecomputeContextResponse(request.contextId)
          case Left(e)   => Api.ExecutionFailed(request.contextId, e)
        }
      }
      ctx.endpoint.sendToClient(Api.Response(maybeRequestId, payload))
    } else {
      ctx.endpoint.sendToClient(
        Api
          .Response(maybeRequestId, Api.ContextNotExistError(request.contextId))
      )
    }
  }

}
