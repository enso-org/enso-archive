package org.enso.interpreter.instrument.job

import java.io.File

import org.enso.interpreter.instrument.execution.RuntimeContext

class OpenFileJob(file: File, contents: String)
    extends Job[Unit](List.empty, false, false) {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.locking.acquireFileLock(file)
    ctx.locking.acquireReadCompilationLock()
    try {
      ctx.executionService.setModuleSources(file, contents)
    } finally {
      ctx.locking.releaseReadCompilationLock()
      ctx.locking.releaseFileLock(file)
    }
  }

}
