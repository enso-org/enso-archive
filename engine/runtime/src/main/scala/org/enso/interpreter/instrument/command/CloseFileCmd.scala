package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api

class CloseFileCmd(request: Api.CloseFileNotification) extends Command {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    ctx.executionService.resetModuleSources(request.path)
  }

}
