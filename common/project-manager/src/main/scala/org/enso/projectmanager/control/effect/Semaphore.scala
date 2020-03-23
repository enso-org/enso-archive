package org.enso.projectmanager.control.effect

import org.enso.projectmanager.control.core.CovariantFlatMap

trait Semaphore[F[+_, +_]] {

  def acquire(): F[Nothing, Unit]

  def release(): F[Nothing, Unit]

  def withPermit[E, A](block: F[E, A]): F[E, A]

}

object Semaphore {

  def unsafeMake[F[+_, +_]: Sync: Except: CovariantFlatMap](
    permits: Int
  ): Semaphore[F] =
    new JvmSemaphore(permits)

}
