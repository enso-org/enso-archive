package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.CloseFileJob
import org.enso.polyglot.runtime.Runtime.Api

import scala.concurrent.{ExecutionContext, Future}

/**
  * A command that closes a file.
  *
  * @param request a request for a service
  */
class CloseFileCmd(request: Api.CloseFileNotification) extends Command {

  /** @inheritdoc **/
  override def execute(
    implicit ctx: RuntimeContext,
    ec: ExecutionContext
  ): Future[Unit] =
    ctx.jobProcessor.run(new CloseFileJob(request.path))

}
