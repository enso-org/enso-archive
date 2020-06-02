package org.enso.interpreter.instrument.job

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api

class ExecuteJob(contextId: Api.ContextId, stack: List[InstrumentFrame])
    extends Job[Unit](List(contextId), true)
    with ProgramExecutionSupport {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.lockRegistry.getContextLock(contextId).lock()
    ctx.lockRegistry.getCompilationLock().readLock().lock()
    try {
      runProgram(contextId, stack)
    } finally {
      ctx.lockRegistry.getCompilationLock().readLock().unlock()
      ctx.lockRegistry.getContextLock(contextId).unlock()
    }
  }

}
