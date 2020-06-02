package org.enso.interpreter.instrument.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Callable, Executors}
import java.util.logging.Level

import org.enso.interpreter.instrument.InterpreterContext
import org.enso.interpreter.instrument.command.Command
import org.enso.interpreter.instrument.execution.Completion.{Done, Interrupted}
import org.enso.interpreter.instrument.job.Job

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/**
  * This component schedules the execution of commands. It keep a queue of
  * pending commands. It activates command execution in FIFO order.
  *
  * @param parallelism the size of the underlying compute thread pool
  */
class ExecutionEngine(
  parallelism: Int,
  interpreterContext: InterpreterContext
) extends CommandProcessor
    with JobProcessor
    with JobControlPlane {

  private val runningJobsRef =
    new AtomicReference[Vector[RunningJob]](Vector.empty)

  private val context = interpreterContext.executionService.getContext

  private val commandExecutor = Executors.newCachedThreadPool(
    new TruffleThreadFactory(context, "command-pool")
  )

  private val jobExecutor = Executors.newFixedThreadPool(
    parallelism,
    new TruffleThreadFactory(context, "job-pool")
  )

  implicit private val commandExecutionContext =
    ExecutionContext.fromExecutor(commandExecutor)

  private val runtimeContext =
    RuntimeContext(
      executionService = interpreterContext.executionService,
      contextManager   = interpreterContext.contextManager,
      endpoint         = interpreterContext.endpoint,
      truffleContext   = interpreterContext.truffleContext,
      commandProcessor = this,
      jobProcessor     = this,
      jobControlPlane  = this,
      locking          = new ReentrantLocking
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

          case Failure(_: InterruptedException) =>
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

  override def run[A](job: Job[A]): Future[A] = {
    val jobId   = UUID.randomUUID()
    val promise = Promise[A]()
    val future = jobExecutor.submit[Unit](new Callable[Unit] {
      override def call(): Unit = {
        val logger = runtimeContext.executionService.getLogger
        logger.log(Level.FINE, s"Executing job: $job...")
        try {
          val result = job.run(runtimeContext)
          logger.log(Level.FINE, s"Job $job finished.")
          promise.success(result)
        } catch {
          case NonFatal(ex) => promise.failure(ex)
        } finally {
          runningJobsRef.updateAndGet(_.filterNot(_.id == jobId))
        }
      }
    })
    val runningJob = RunningJob(jobId, job, future)

    runningJobsRef.updateAndGet(_ :+ runningJob)

    promise.future
  }

  override def abortAllJobs(): Unit = {
    val allJobs         = runningJobsRef.get()
    val cancellableJobs = allJobs.filter(_.job.isCancellable)
    cancellableJobs.foreach(_.future.cancel(true))
  }

  override def abortJobs(contextId: UUID): Unit = {
    val allJobs     = runningJobsRef.get()
    val contextJobs = allJobs.filter(_.job.contextIds.contains(contextId))
    contextJobs.foreach(_.future.cancel(true))
  }

}
