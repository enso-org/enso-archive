package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.CacheInvalidation
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.{
  ApplyEditsJob,
  EnsureCompiledJob,
  ExecuteJob
}
import org.enso.polyglot.runtime.Runtime.Api

import scala.concurrent.{ExecutionContext, Future}

class EditFileCommand(request: Api.EditFileNotification) extends Command {

  /**
    * Executes a request.
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] = {
    for {
      _ <- Future { ctx.jobControlPlane.abortAllJobs() }
      invalidationRules <- ctx.jobProcessor.run(
        new ApplyEditsJob(request.path, request.edits)
      )
      _ <- ctx.jobProcessor.run(new EnsureCompiledJob(List(request.path)))
      _ <- Future.sequence(executeAll(invalidationRules))
    } yield ()
  }

  private def executeAll(
    invalidationRules: Iterable[CacheInvalidation]
  )(implicit ctx: RuntimeContext): List[Future[Unit]] = {
    ctx.contextManager.getAll
      .filter(kv => kv._2.nonEmpty)
      .mapValues(_.toList)
      .toList
      .map {
        case (contextId, stack) =>
          CacheInvalidation.run(stack, invalidationRules)
          ctx.jobProcessor.run(new ExecuteJob(contextId, stack))
      }
  }

}
