package org.enso.languageserver

import java.util.concurrent.{ExecutionException, TimeoutException}

import zio._

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

trait Exec[F[_, _]] {

  def exec[E, A](op: F[E, A]): Future[Either[E, A]]
}

case class ZioExec(runtime: Runtime[ZEnv] = Runtime.default)
    extends Exec[ZioExec.IO] {

  override def exec[E, A](op: ZIO[ZEnv, E, A]): Future[Either[E, A]] = {
    val promise = Promise[Either[E, A]]
    runtime.unsafeRunAsync(op) {
      _.fold(
        ZioExec.completeFailure(promise, _),
        ZioExec.completeSuccess(promise, _)
      )
    }
    promise.future
  }

  def execTimed[E, A](
    timeout: FiniteDuration,
    op: ZIO[ZEnv, E, A]
  ): Future[Either[E, A]] = {
    val promise = Promise[Either[E, A]]
    runtime.unsafeRunAsync(
      op.disconnect.timeout(zio.duration.Duration.fromScala(timeout))
    ) {
      _.fold(
        ZioExec.completeFailure(promise, _),
        _.fold(promise.failure(ZioExec.timeoutFailure))(
          a => promise.success(Right(a))
        )
      )
    }
    promise.future
  }
}

object ZioExec {

  type IO[E, A] = ZIO[ZEnv, E, A]

  object ZioExecutionException extends ExecutionException

  private def completeSuccess[E, A](
    promise: Promise[Either[E, A]],
    result: A
  ): Unit =
    promise.success(Right(result))

  private def completeFailure[E, A](
    promise: Promise[Either[E, A]],
    cause: Cause[E]
  ): Unit =
    cause.failureOption match {
      case Some(e) =>
        promise.success(Left(e))
      case None =>
        val error = cause.defects.headOption.getOrElse(executionFailure)
        promise.failure(error)
    }

  private val executionFailure: Throwable =
    new ExecutionException("ZIO execution failed", ZioExecutionException)

  private val timeoutFailure: Throwable =
    new TimeoutException()
}
