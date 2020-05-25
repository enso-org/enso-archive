package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext

trait Command {

  def execute(implicit ctx: RuntimeContext): Unit

}
