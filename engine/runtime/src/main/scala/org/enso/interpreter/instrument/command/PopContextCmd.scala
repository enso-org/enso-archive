package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.{CacheInvalidation, InstrumentFrame}
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

/**
  * A command that pops an item from a stack.
  *
  * == Caching ==
  *
  * Cache is copied to the top frame from the popped frame. It may happen that
  * cache had been invalidated with the text edit, and the popped frame contains
  * the latest precomputed values.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class PopContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.PopContextRequest
) extends Command
    with ProgramExecutionSupport {

  /** @inheritdoc **/
  override def execute(implicit ctx: RuntimeContext): Unit = {
    if (ctx.contextManager.get(request.contextId).isDefined) {
      val payload = ctx.contextManager.pop(request.contextId) match {
        case Some(InstrumentFrame(_: Api.StackItem.ExplicitCall, _)) =>
          Api.PopContextResponse(request.contextId)
        case Some(InstrumentFrame(_: Api.StackItem.LocalCall, cache)) =>
          val stack = ctx.contextManager.getStack(request.contextId)
          CacheInvalidation.run(
            stack,
            CacheInvalidation(
              CacheInvalidation.StackSelector.Top,
              CacheInvalidation.Command.CopyCache(cache)
            )
          )
          withContext(runProgram(request.contextId, stack.toList)) match {
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
