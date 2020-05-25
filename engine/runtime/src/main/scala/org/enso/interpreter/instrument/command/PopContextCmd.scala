package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

class PopContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.PopContextRequest
) extends Command
    with EnsoExecutionSupport {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    if (ctx.contextManager.get(request.contextId).isDefined) {
      val payload = ctx.contextManager.pop(request.contextId) match {
        case Some(_: Api.StackItem.ExplicitCall) =>
          Api.PopContextResponse(request.contextId)
        case Some(_: Api.StackItem.LocalCall) =>
          val stack = ctx.contextManager.getStack(request.contextId)
          withContext(runEnso(request.contextId, stack.toList)) match {
            case Right(()) => Api.PopContextResponse(request.contextId)
            case Left(e)   => Api.ExecutionFailed(request.contextId, e)
          }
        case None =>
          Api.EmptyStackError(request.contextId)
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
