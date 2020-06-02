package org.enso.interpreter.instrument.job

import java.util.UUID

import org.enso.interpreter.instrument.execution.RuntimeContext

abstract class Job[+A](val contextIds: List[UUID], val isCancellable: Boolean) {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  def run(implicit ctx: RuntimeContext): A

  override def toString: String = this.getClass.getSimpleName

}
