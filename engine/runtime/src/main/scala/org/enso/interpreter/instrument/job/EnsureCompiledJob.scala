package org.enso.interpreter.instrument.job

import java.io.File

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.EnsureCompiledJob.extractFilesFromStack
import org.enso.polyglot.runtime.Runtime.Api.StackItem.ExplicitCall

import scala.collection.mutable
import scala.jdk.OptionConverters._

class EnsureCompiledJob(files: List[File])
    extends Job[Unit](List.empty, true, false) {

  def this(stack: mutable.Stack[InstrumentFrame]) =
    this(extractFilesFromStack(stack))

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

object EnsureCompiledJob {

  def extractFilesFromStack(stack: mutable.Stack[InstrumentFrame]): List[File] =
    stack
      .map(_.item)
      .collect {
        case ExplicitCall(methodPointer, _, _) => methodPointer.file
      }
      .toList

}
