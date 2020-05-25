package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

class AttachVisualisationCmd(
  maybeRequestId: Option[RequestId],
  request: Api.AttachVisualisation
) extends BaseVisualisationCmd {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    if (ctx.contextManager.contains(
          request.visualisationConfig.executionContextId
        )) {
      upsertVisualisation(
        maybeRequestId,
        request.visualisationId,
        request.expressionId,
        request.visualisationConfig,
        Api.VisualisationAttached()
      )
    } else {
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
