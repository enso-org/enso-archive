package org.enso.interpreter.instrument.job

import java.util.UUID

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}

class ExecuteJob(contextId: UUID, stack: List[InstrumentFrame])
    extends Job[Unit](List(contextId), true, true)
    with ProgramExecutionSupport {

  def this(exe: Executable) = this(exe.contextId, exe.stack.toList)

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.locking.acquireContextLock(contextId)
    ctx.locking.acquireReadCompilationLock()
    try {
      withContext {
        runProgram(contextId, stack)
      }
    } finally {
      ctx.locking.releaseReadCompilationLock()
      ctx.locking.releaseContextLock(contextId)
    }
  }

}
