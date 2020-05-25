package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api

class OpenFileCmd(request: Api.OpenFileNotification) extends Command {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    ctx.executionService.setModuleSources(request.path, request.contents)
  }

}
