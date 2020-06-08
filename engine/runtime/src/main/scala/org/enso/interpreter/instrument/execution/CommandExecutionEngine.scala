package org.enso.interpreter.instrument.execution

import java.util.concurrent.Executors
import java.util.logging.Level

import org.enso.interpreter.instrument.InterpreterContext
import org.enso.interpreter.instrument.command.Command
import org.enso.interpreter.instrument.execution.Completion.{Done, Interrupted}
import org.enso.interpreter.runtime.control.ThreadInterruptedException

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * This component schedules the execution of commands. It keep a queue of
  * pending commands. It activates command execution in FIFO order.
  */
class CommandExecutionEngine(
  interpreterContext: InterpreterContext
) extends CommandProcessor {

  private val context = interpreterContext.executionService.getContext

  private val commandExecutor = Executors.newCachedThreadPool(
    new TruffleThreadFactory(context, "command-pool")
  )

  implicit private val commandExecutionContext =
    ExecutionContext.fromExecutor(commandExecutor)

  private val locking = new ReentrantLocking

  private val jobExecutionEngine =
    new JobExecutionEngine(interpreterContext, locking)

  private val runtimeContext =
    RuntimeContext(
      executionService = interpreterContext.executionService,
      contextManager   = interpreterContext.contextManager,
      endpoint         = interpreterContext.endpoint,
      truffleContext   = interpreterContext.truffleContext,
      jobProcessor     = jobExecutionEngine,
      jobControlPlane  = jobExecutionEngine,
      locking          = locking
    )

  /** @inheritdoc **/
  def invoke(cmd: Command): Future[Completion] = {
    val logger = runtimeContext.executionService.getLogger
    val doIt = () =>
      cmd
        .execute(runtimeContext, commandExecutionContext)
        .transformWith[Completion] {
          case Success(()) =>
            Future.successful(Done)

          case Failure(
              _: InterruptedException | _: ThreadInterruptedException
              ) =>
            Future.successful[Completion](Interrupted)

          case Failure(NonFatal(ex)) =>
            logger.log(
              Level.SEVERE,
              s"An error occurred during execution of $cmd command",
              ex
            )
            Future.failed[Completion](ex)
        }

    for {
      _ <- Future { logger.log(Level.FINE, s"Executing command: $cmd...") }
      _ <- doIt()
      _ <- Future { logger.log(Level.FINE, s"Command $cmd finished.") }
    } yield Done

  }

}
