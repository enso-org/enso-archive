package org.enso.interpreter.instrument.execution

import org.enso.interpreter.instrument.command.Command
import org.enso.interpreter.instrument.execution.CommandProcessor.Done

import scala.concurrent.Future

trait CommandProcessor {

  def invoke(cmd: Command, ctx: RuntimeContext): Future[Done.type]

}

object CommandProcessor {

  case object Done

}
