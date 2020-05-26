package org.enso.interpreter.instrument.job

import java.util.UUID

import org.enso.interpreter.instrument.execution.RuntimeContext

abstract class Job(val contextIds: List[UUID]) {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  def run(ctx: RuntimeContext): Unit

}
