package org.enso.interpreter.instrument

import java.util.UUID

import org.enso.compiler.pass.analyse.CachePreferenceAnalysis
import org.enso.polyglot.runtime.Runtime.Api

import scala.jdk.CollectionConverters._

/**
  * Cache invalidation command.
  *
  * @param elements the cache of which stack elements to invalidate.
  * @param command the invalidation command.
  * @param indexes the indexes to invalidate.
  */
case class CacheInvalidation(elements: CacheInvalidation.StackSelector, command: CacheInvalidation.Command, indexes: Set[CacheInvalidation.IndexSelector])

object CacheInvalidation {

  /** Selector of the cache index. */
  sealed trait IndexSelector

  object IndexSelector {

    /** Invalidate value from indexes. */
    case object All extends IndexSelector
  }

  /** Base trait for cache invalidation instructions. */
  sealed trait Command

  object Command {

    /** Instruction to invalidate all cache entries. */
    case object InvalidateAll extends Command

    /**
      * Instruction to invalidate provided cache keys.
      *
      * @param keys a list of keys that should be invalidated.
      */
    case class InvalidateKeys(keys: Iterable[UUID]) extends Command

    /**
      * Instruction to invalidate stale entries from the cache.
      *
      * @param scope all ids of the source.
      */
    case class InvalidateStale(scope: Iterable[UUID]) extends Command

    /**
      * Instruction to set the cache from the source.
      *
      * @param source the source runtime cache.
      */
    case class CopyCache(source: RuntimeCache) extends Command

    /**
      * Set cache metadata form the compiler pass.
      *
      * @param metadata the cache metadata.
      */
    case class SetMetadata(metadata: CachePreferenceAnalysis.Metadata) extends Command

    /**
      * Create an invalidation instruction from [[Api.InvalidatedExpressions]].
      *
      * @param expressions invalidated expressions.
      * @return an invalidation instruction.
      */
    def apply(expressions: Api.InvalidatedExpressions): Command =
      expressions match {
        case Api.InvalidatedExpressions.All() =>
          InvalidateAll
        case Api.InvalidatedExpressions.Expressions(ids) =>
          InvalidateKeys(ids)
      }
  }

  /** Base trait for selecting stack elements. */
  sealed trait StackSelector

  object StackSelector {

    /** Select all stack elements. */
    case object All extends StackSelector

    /** Select top stack element. */
    case object Top extends StackSelector
  }


  /**
    * Create an invalidation instruction.
    *
    * @param elements the stack elements selector.
    * @param command the invalidation command.
    */
  def apply(elements: StackSelector, command: Command): CacheInvalidation =
    new CacheInvalidation(elements, command, Set())

  /**
   * Run cache invalidation batch.
   *
   * @param stack the runtime stack.
   * @param instructions the list of cache invalidation instructions.
   */
  def runAll(stack: Iterable[InstrumentFrame], instructions: Iterable[CacheInvalidation]): Unit =
    instructions.foreach(run(stack, _))

  /**
   * Run cache invalidation.
   *
   * @param stack the runtime stack.
   * @param instruction the invalidation instruction.
   */
  def run(stack: Iterable[InstrumentFrame], instruction: CacheInvalidation): Unit = {
    val frames = instruction.elements match {
      case StackSelector.All => stack
      case StackSelector.Top => stack.headOption.toSeq
    }
    run(frames, instruction.command, instruction.indexes)
  }

  /**
    * Run cache invalidation.
    *
    * @param frames stack elements which cache should be invalidated.
    * @param command the invalidation instruction.
    * @param indexes the list of indexes to invalidate.
    */
  private def run(frames: Iterable[InstrumentFrame], command: Command, indexes: Set[IndexSelector]): Unit = {
    frames.foreach(run(_, command, indexes))
  }

  /**
    * Run cache invalidation.
    *
    * @param frame stack element to invalidate
    * @param command the invalidation instruction.
    * @param indexes the list of indexes to invalidate.
    */
  private def run(frame: InstrumentFrame, command: Command, indexes: Set[IndexSelector]): Unit =
    command match {
      case Command.InvalidateAll =>
        frame.cache.clear()
        if (indexes.contains(IndexSelector.All)) frame.cache.clearWeights()
      case Command.InvalidateKeys(keys) =>
        keys.foreach { key =>
          frame.cache.remove(key)
          if (indexes.contains(IndexSelector.All)) frame.cache.removeWeight(key)
        }
      case Command.InvalidateStale(scope) =>
        val staleKeys = frame.cache.getKeys.asScala.diff(scope.toSet)
        staleKeys.foreach { key =>
          frame.cache.remove(key)
          if (indexes.contains(IndexSelector.All)) frame.cache.removeWeight(key)
        }
      case Command.CopyCache(source) =>
        frame.copy(cache = source)
      case Command.SetMetadata(metadata) =>
        frame.cache.setWeights(metadata.weights)
    }
}
