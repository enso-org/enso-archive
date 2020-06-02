package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.OpenFileJob
import org.enso.polyglot.runtime.Runtime.Api

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that opens a file.
  *
  * @param request a request for a service
  */
class OpenFileCmd(request: Api.OpenFileNotification) extends Command {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    ctx.jobProcessor.run(new OpenFileJob(request.path, request.contents))

}
