package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}
import org.enso.interpreter.instrument.job.{
  EnsureCompiledJob,
  ExecuteJob,
  ProgramExecutionSupport
}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.runtime.Runtime.Api.{RequestId, StackItem}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that pushes an item onto a stack.
  *
  * @param maybeRequestId an option with request id
  * @param request a request for a service
  */
class PushContextCmd(
  maybeRequestId: Option[RequestId],
  request: Api.PushContextRequest
) extends Command(maybeRequestId)
    with ProgramExecutionSupport {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    if (ctx.contextManager.get(request.contextId).isDefined) {
      Future {
        ctx.jobControlPlane.abortJobs(request.contextId)
        val pushed = pushItemOntoStack()
        if (pushed) {
          reply(Api.PushContextResponse(request.contextId))
        } else {
          reply(Api.InvalidStackItemError(request.contextId))
        }
        pushed
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

  private def pushItemOntoStack()(implicit ctx: RuntimeContext): Boolean = {
    val stack = ctx.contextManager.getStack(request.contextId)
    request.stackItem match {
      case _: Api.StackItem.ExplicitCall if stack.isEmpty =>
        ctx.contextManager.push(request.contextId, request.stackItem)
        true

      case _: Api.StackItem.LocalCall if stack.nonEmpty =>
        ctx.contextManager.push(request.contextId, request.stackItem)
        true

      case _ =>
        false
    }
  }

}
