package org.enso.projectmanager.control.effect

import java.io.IOException

import zio._
import zio.blocking
import zio.duration.Duration

import scala.concurrent.duration.FiniteDuration

object ZioSync extends Sync[ZIO[ZEnv, +*, +*]] {

  override def effect[A](effect: => A): ZIO[ZEnv, Nothing, A] =
    ZIO.effectTotal(effect)

  override def blockingOp[A](effect: => A): ZIO[ZEnv, Throwable, A] =
    blocking.effectBlocking(effect)

  override def blockingIO[A](effect: => A): ZIO[ZEnv, IOException, A] =
    blocking.effectBlockingIO(effect)

  override def timeoutFail[E, E1 >: E, A](fa: ZIO[ZEnv, E, A])(e: E1)(
    timeout: FiniteDuration
  ): ZIO[zio.ZEnv, E1, A] = fa.timeoutFail(e)(Duration.fromScala(timeout))

}
