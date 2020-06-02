package org.enso.interpreter.instrument.job
import java.io.File

import org.enso.interpreter.instrument.CacheInvalidation
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.text.editing.model.TextEdit

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class ApplyEditsJob(file: File, edits: Seq[TextEdit])
    extends Job[List[CacheInvalidation]](List.empty, false) {

  /**
    *
    * @param ctx contains suppliers of services to perform a request
    */
  override def run(implicit ctx: RuntimeContext): List[CacheInvalidation] = {
    ctx.locking.acquireFileLock(file)
    ctx.locking.acquireReadCompilationLock()
    try {
      val maybeChangeSet =
        ctx.executionService
          .modifyModuleSources(file, edits.asJava)
          .toScala

      val invalidateExpressions = maybeChangeSet.map { changeset =>
        CacheInvalidation.InvalidateKeys(
          edits.flatMap(changeset.compute)
        )
      }
      val invalidateStale = maybeChangeSet.map { changeset =>
        val scopeIds = ctx.executionService.getContext.getCompiler
          .parseMeta(changeset.source.toString)
          .map(_._2)
        CacheInvalidation.InvalidateStale(scopeIds)
      }

      invalidateExpressions.toList ++ invalidateStale.toList
    } finally {
      ctx.locking.releaseReadCompilationLock()
      ctx.locking.releaseFileLock(file)
    }
  }

}
