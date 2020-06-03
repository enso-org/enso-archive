package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.CacheInvalidation
import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}
import org.enso.interpreter.instrument.job.{
  EnsureCompiledJob,
  ExecuteJob,
  ProgramExecutionSupport
}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.RequestId

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that forces a recomputation of the current position.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class RecomputeContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.RecomputeContextRequest
) extends Command(maybeRequestId) {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    if (ctx.contextManager.get(request.contextId).isDefined) {
      Future {
        ctx.jobControlPlane.abortJobs(request.contextId)
        val stack = ctx.contextManager.getStack(request.contextId)
        if (stack.isEmpty) {
          reply(Api.EmptyStackError(request.contextId))
          false
        } else {
          CacheInvalidation.run(
            stack,
            request.expressions.toSeq.map(CacheInvalidation(_))
          )
          reply(Api.RecomputeContextResponse(request.contextId))
          true
        }
      } flatMap {
        case false => Future.successful(())
        case true =>
          val stack      = ctx.contextManager.getStack(request.contextId)
          val executable = Executable(request.contextId, stack)
          for {
            _ <- ctx.jobProcessor.run(new EnsureCompiledJob(executable.stack))
            _ <- ctx.jobProcessor.run(new ExecuteJob(executable))
          } yield ()
      }
    } else {
      Future {
        reply(Api.ContextNotExistError(request.contextId))
      }
    }

}
