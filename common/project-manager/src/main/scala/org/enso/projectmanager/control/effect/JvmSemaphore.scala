package org.enso.projectmanager.control.effect

import java.util.concurrent

import org.enso.projectmanager.control.core.CovariantFlatMap
import org.enso.projectmanager.control.core.syntax._
import syntax._

class JvmSemaphore[F[+_, +_]: Sync: Except: CovariantFlatMap](permits: Int)
    extends Semaphore[F] {

  private val semaphore = new concurrent.Semaphore(permits, true)

  override def acquire(): F[Nothing, Unit] =
    Sync[F].effectTotal(semaphore.acquire())

  override def release(): F[Nothing, Unit] =
    Sync[F].effectTotal(semaphore.release())

  override def withPermit[E, A](block: F[E, A]): F[E, A] =
    for {
      _      <- acquire()
      result <- block.onDie(_ => release()).onError(_ => release())
      _      <- release()
    } yield result

}
