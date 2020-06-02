package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
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
    Future {
      ctx.executionService.setModuleSources(request.path, request.contents)
    }

}
