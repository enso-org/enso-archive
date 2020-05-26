package org.enso.compiler.pass

import java.util.UUID

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.exception.CompilerError

import scala.collection.mutable

// TODO [AA] Account for cycles and throw an error "Dependency cycle found in
//  pass ordering"
// TODO [AA] Fixed position precursors duplicate passes where necessary.
// TODO [AA] Invalidated passes need to be accounted for.

/** The pass manager is responsible for executing the provided passes in order.
  *
  * @param passes the specification of the ordering for the passes
  * @param passConfiguration the configuration for the passes
  */
//noinspection DuplicatedCode
class PassManager(
  passes: List[IRPass],
  passConfiguration: PassConfiguration
) {
  val passOrdering: List[IRPass] = computePassOrdering(passes)

  /** Computes a valid pass ordering for the compiler.
   *
   * @param passes the input list of passes
   * @throws CompilerError if a valid pass ordering cannot be computed
   * @return a valid pass ordering for the compiler, based on `passes`
   */
  @throws[CompilerError]
  def computePassOrdering(passes: List[IRPass]): List[IRPass] = {
    passes
  }

  /** Calculates the number of times each pass occurs in the pass ordering.
    *
    * @return the a mapping from the pass identifier to the number of times the
    *         pass occurs */
  def calculatePassCounts: mutable.Map[UUID, PassCount] = {
    val passCounts: mutable.Map[UUID, PassCount] = mutable.Map()

    for (pass <- passOrdering) {
      passCounts.get(pass.key) match {
        case Some(counts) =>
          passCounts(pass.key) = counts.copy(available = counts.available + 1)
        case None => passCounts(pass.key) = PassCount()
      }
    }

    passCounts
  }

  /** Executes the passes on an [[IR.Module]].
    *
    * @param ir the module to execute the compiler phases on
    * @param moduleContext the module context in which the phases are executed
    * @return the result of executing [[passOrdering]] on `ir`
    */
  def runPassesOnModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = {
    val passCounts = calculatePassCounts

    val newContext =
      moduleContext.copy(passConfiguration = Some(passConfiguration))

    passOrdering.foldLeft(ir)((intermediateIR, pass) => {
      val passCount = passCounts(pass.key)

      passConfiguration
        .get(pass)
        .foreach(c =>
          c.shouldWriteToContext =
            passCount.available - passCount.completed == 1
        )

      val result = pass.runModule(intermediateIR, newContext)

      passCounts(pass.key) = passCount.copy(completed = passCount.completed + 1)

      result
    })
  }

  /** Executes the passes on an [[IR.Expression]].
    *
    * @param ir the expression to execute the compiler phases on
    * @param inlineContext the inline context in which the expression is
    *                      processed
    * @return the result of executing [[passOrdering]] on `ir`
    */
  def runPassesInline(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = {
    val passCounts = calculatePassCounts

    val newContext =
      inlineContext.copy(passConfiguration = Some(passConfiguration))

    passOrdering.foldLeft(ir)((intermediateIR, pass) => {
      val passCount = passCounts(pass.key)

      passConfiguration
        .get(pass)
        .foreach(c =>
          c.shouldWriteToContext =
            passCount.available - passCount.completed == 1
        )

      val result = pass.runExpression(intermediateIR, newContext)

      passCounts(pass.key) = passCount.copy(completed = passCount.completed + 1)

      result
    })
  }

  /** The counts of passes running.
    *
    * @param available how many runs should occur
    * @param completed how many runs have been completed
    */
  sealed case class PassCount(available: Int = 1, completed: Int = 0)
}
