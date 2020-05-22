package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.RuntimeContext

trait Command {

  def execute(implicit ctx: RuntimeContext): Unit

}
