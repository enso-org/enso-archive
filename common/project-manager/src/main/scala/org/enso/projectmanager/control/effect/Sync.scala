package org.enso.projectmanager.control.effect

import java.io.IOException

import zio.{ZEnv, ZIO}

import scala.concurrent.duration.FiniteDuration

/**
  * synchronous effect that does blocking IO into a pure value
  * @tparam F
  */
trait Sync[F[+_, +_]] {

  def effect[A](effect: => A): F[Nothing, A]

  def blockingOp[A](effect: => A): F[Throwable, A]

  def blockingIO[A](effect: => A): F[IOException, A]

  def timeoutFail[E, E1 >: E, A](fa: F[E, A])(e: E1)(
    timeout: FiniteDuration
  ): F[E1, A]

}

object Sync {

  def apply[F[+_, +_]](implicit sync: Sync[F]): Sync[F] = sync

  implicit val zioSync: Sync[ZIO[ZEnv, +*, +*]] = ZioSync

}
