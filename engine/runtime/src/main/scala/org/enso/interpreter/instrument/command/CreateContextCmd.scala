package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

class CreateContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.CreateContextRequest
) extends Command {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    ctx.contextManager.create(request.contextId)
    ctx.endpoint.sendToClient(
      Api.Response(maybeRequestId, Api.CreateContextResponse(request.contextId))
    )
  }

}
