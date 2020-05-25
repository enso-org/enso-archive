package org.enso.interpreter.instrument.command

import org.enso.interpreter.instrument.execution.RuntimeContext
import org.enso.polyglot.runtime.Runtime.Api

import scala.jdk.CollectionConverters._

class EditFileCmd(request: Api.EditFileNotification)
    extends Command
    with EnsoExecutionSupport {

  override def execute(implicit ctx: RuntimeContext): Unit = {
    ctx.executionService.modifyModuleSources(request.path, request.edits.asJava)
    withContext(executeAll())
  }

  private def executeAll()(implicit ctx: RuntimeContext): Unit =
    ctx.contextManager.getAll
      .filter(kv => kv._2.nonEmpty)
      .mapValues(_.toList)
      .foreach(Function.tupled(runEnso))

}
