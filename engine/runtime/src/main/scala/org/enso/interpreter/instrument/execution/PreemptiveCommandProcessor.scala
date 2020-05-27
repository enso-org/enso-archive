package org.enso.interpreter.instrument.execution

import java.util.concurrent.{Callable, Executors}
import java.util.logging.Level

import org.enso.interpreter.instrument.InterpreterContext
import org.enso.interpreter.instrument.command.Command
import org.enso.interpreter.instrument.job.Job

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

/**
  * This component schedules the execution of commands. It keep a queue of
  * pending commands. It activates command execution in FIFO order.
  *
  * @param parallelism the size of the underlying compute thread pool
  */
class PreemptiveCommandProcessor(
  parallelism: Int,
  interpreterContext: InterpreterContext
) extends CommandProcessor
    with JobProcessor {

  private val context = interpreterContext.executionService.getContext

  private val commandExecutor = Executors.newSingleThreadExecutor(
    new TruffleThreadFactory(context, "enso-orchestrator-pool")
  )

  @unused
  private val jobExecutor = Executors.newFixedThreadPool(
    parallelism,
    new TruffleThreadFactory(context, "enso-compute-pool")
  )

  private val runtimeContext =
    RuntimeContext(
      executionService        = interpreterContext.executionService,
      contextManager          = interpreterContext.contextManager,
      endpoint                = interpreterContext.endpoint,
      truffleContext          = interpreterContext.truffleContext,
      cache                   = interpreterContext.cache,
      commandProcessor        = this,
      commandExecutionContext = ExecutionContext.fromExecutor(commandExecutor),
      jobProcessor            = this
    )

  /** @inheritdoc **/
  def invoke(cmd: Command): Future[Done.type] = {
    val promise = Promise[Done.type]()
    commandExecutor.submit[Unit](new Callable[Unit] {
      override def call(): Unit = {
        val logger = runtimeContext.executionService.getLogger
        logger.log(Level.FINE, s"Executing command: $cmd...")
        try {
          cmd.execute(runtimeContext)
          logger.log(Level.FINE, s"Command $cmd finished.")
          promise.success(Done)
        } catch {
          case NonFatal(ex) => promise.failure(ex)
        }
      }
    })

    promise.future
  }

  override def run(job: Job): Future[Done.type] = ???
}
