package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api

class EditFileCommand(request: Api.EditFileNotification) extends Command {

  /**
    * Executes a request.
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def execute(implicit ctx: RuntimeContext): Unit = {}

}
