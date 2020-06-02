package org.enso.interpreter.instrument.job

import java.io.File

import org.enso.interpreter.instrument.execution.RuntimeContext

import scala.jdk.OptionConverters._

class EnsureCompiledJob(files: List[File])
    extends Job[Unit](List.empty, true, false) {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.locking.acquireWriteCompilationLock()
    try {
      files
        .flatMap { file =>
          ctx.executionService.getContext.getModuleForFile(file).toScala.toList
        }
        .foreach { module =>
          module.parseScope(ctx.executionService.getContext)
        }
    } finally {
      ctx.locking.releaseWriteCompilationLock()
    }
  }

}
