package org.enso.interpreter.instrument.job

import org.enso.interpreter.instrument.execution.RuntimeContext

object TruffleUtils {

  /**
    * Executes action in a newly created Truffle context.
    *
    * @param action an action
    * @param ctx a runtime context
    * @return a result of executing the action
    */
  def withContext[A](action: => A)(implicit ctx: RuntimeContext): A = {
    val token = ctx.truffleContext.enter()
    try {
      action
    } finally {
      ctx.truffleContext.leave(token)
    }
  }

}
