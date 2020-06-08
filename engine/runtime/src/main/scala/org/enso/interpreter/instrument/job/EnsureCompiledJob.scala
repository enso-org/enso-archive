package org.enso.interpreter.instrument.job

import java.io.File

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.instrument.job.EnsureCompiledJob.extractFilesFromStack
import org.enso.polyglot.runtime.Runtime.Api.StackItem.ExplicitCall

import scala.collection.mutable
import scala.jdk.OptionConverters._

/**
  * A job that ensures that specified files are compiled.
  *
  * @param files a files to compile
  */
class EnsureCompiledJob(files: List[File])
    extends Job[Unit](List.empty, true, false) {

  /**
    * Ensures that all files on the provided stack are compiled.
    *
    * @param stack a call stack
    */
  def this(stack: mutable.Stack[InstrumentFrame]) =
    this(extractFilesFromStack(stack))

  /** @inheritdoc **/
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

  /**
    * Extracts files to compile from a call stack.
    *
    * @param stack a call stack
    * @return a list of files to compile
    */
  def extractFilesFromStack(stack: mutable.Stack[InstrumentFrame]): List[File] =
    stack
      .map(_.item)
      .collect {
        case ExplicitCall(methodPointer, _, _) => methodPointer.file
      }
      .toList

}
