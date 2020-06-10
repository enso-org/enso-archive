package org.enso.interpreter.instrument.job

import java.io.File

import org.enso.interpreter.instrument.{CacheInvalidation, InstrumentFrame}
import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.interpreter.runtime.Module
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.editing.model.TextEdit

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._
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
    this(EnsureCompiledJob.extractFilesFromStack(stack))

  /**
    * Ensures that a files is compiled after applying the edits
    *
    * @param file a file to compile
    */
  def this(file: File, edits: Seq[TextEdit]) = {
    this(List(file))
    EnsureCompiledJob.enqueueEdits(file, edits)
  }

  /** @inheritdoc **/
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.locking.acquireWriteCompilationLock()
    try {
      val modules = files.flatMap(compile)
      val invalidationCommands = files.flatMap { file =>
        applyEdits(file, EnsureCompiledJob.dequeueEdits(file))
      }
      runInvalidation(invalidationCommands)
      modules.foreach(compile)
    } finally {
      ctx.locking.releaseWriteCompilationLock()
    }
  }

  private def compile(
    file: File
  )(implicit ctx: RuntimeContext): Option[Module] = {
    ctx.executionService.getContext
      .getModuleForFile(file)
      .map(compile(_))
      .toScala
  }

  private def compile(module: Module)(implicit ctx: RuntimeContext): Module =
    module.parseScope(ctx.executionService.getContext).getModule

  private def applyEdits(file: File, edits: Seq[TextEdit])(
    implicit ctx: RuntimeContext
  ): Iterable[CacheInvalidation] = {
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
      (invalidateExpressionsCommand.toSeq ++ invalidateStaleCommand.toSeq)
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

  private def runInvalidation(
    invalidationCommands: Iterable[CacheInvalidation]
  )(implicit ctx: RuntimeContext): Unit = {
    ctx.contextManager.getAll.valuesIterator
      .collect {
        case stack if stack.nonEmpty =>
          CacheInvalidation.runAll(stack, invalidationCommands)
      }
  }

}

object EnsureCompiledJob {

  private val unappliedEdits =
    new TrieMap[File, Seq[TextEdit]]().withDefaultValue(Seq())

  private def dequeueEdits(file: File): Seq[TextEdit] =
    unappliedEdits.remove(file).get

  private def enqueueEdits(file: File, edits: Seq[TextEdit]): Unit =
    unappliedEdits.updateWith(file) {
      case Some(v) => Some(v :++ edits)
      case None    => Some(edits)
    }

  /**
    * Extracts files to compile from a call stack.
    *
    * @param stack a call stack
    * @return a list of files to compile
    */
  private def extractFilesFromStack(
    stack: mutable.Stack[InstrumentFrame]
  ): List[File] =
    stack
      .map(_.item)
      .collect {
        case Api.StackItem.ExplicitCall(methodPointer, _, _) =>
          methodPointer.file
      }
      .toList
}
