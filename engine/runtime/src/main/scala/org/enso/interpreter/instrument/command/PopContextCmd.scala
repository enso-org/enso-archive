package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}
import org.enso.interpreter.instrument.job.{EnsureCompiledJob, ExecuteJob}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that pops an item from a stack.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class PopContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.PopContextRequest
) extends Command(maybeRequestId) {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    if (ctx.contextManager.get(request.contextId).isDefined) {
      Future {
        ctx.jobControlPlane.abortJobs(request.contextId)
        val maybeTopItem = ctx.contextManager.pop(request.contextId)
        if (maybeTopItem.isDefined) {
          reply(Api.PopContextResponse(request.contextId))
        } else {
          reply(Api.EmptyStackError(request.contextId))
        }
      } flatMap { _ => scheduleExecutionIfNeeded() }
    } else {
      Future {
        reply(Api.ContextNotExistError(request.contextId))
      }
    }

  private def scheduleExecutionIfNeeded()(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] = {
    val stack = ctx.contextManager.getStack(request.contextId)
    if (stack.nonEmpty) {
      val executable = Executable(request.contextId, stack)
      for {
        _ <- ctx.jobProcessor.run(new EnsureCompiledJob(executable.stack))
        _ <- ctx.jobProcessor.run(new ExecuteJob(executable))
      } yield ()
    } else {
      Future.successful(())
    }
  }

}
