package org.enso.interpreter.instrument.job
import java.io.File

import org.enso.interpreter.instrument.CacheInvalidation
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.text.editing.model.TextEdit

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/**
  * A job that applies text edits to a file.
  *
  * @param file a file to edit
  * @param edits a sequence of text edits
  */
class ApplyEditsJob(file: File, edits: Seq[TextEdit])
    extends Job[List[CacheInvalidation]](List.empty, false, false) {

  /** @inheritdoc **/
  override def run(implicit ctx: RuntimeContext): List[CacheInvalidation] = {
    ctx.locking.acquireFileLock(file)
    ctx.locking.acquireReadCompilationLock()
    try {
      val changesetOpt =
        ctx.executionService
          .modifyModuleSources(file, edits.asJava)
          .toScala
      val invalidateExpressionsCommand = changesetOpt.map { changeset =>
        CacheInvalidation.Command.InvalidateKeys(
          changeset.compute(edits)
        )
      }
      val invalidateStaleCommand = changesetOpt.map { changeset =>
        val scopeIds = ctx.executionService.getContext.getCompiler
          .parseMeta(changeset.source.toString)
          .map(_._2)
        CacheInvalidation.Command.InvalidateStale(scopeIds)
      }
      (invalidateExpressionsCommand.toList ++ invalidateStaleCommand.toList)
        .map(
          CacheInvalidation(
            CacheInvalidation.StackSelector.All,
            _,
            Set(CacheInvalidation.IndexSelector.All)
          )
        )
    } finally {
      ctx.locking.releaseReadCompilationLock()
      ctx.locking.releaseFileLock(file)
    }
  }

}
