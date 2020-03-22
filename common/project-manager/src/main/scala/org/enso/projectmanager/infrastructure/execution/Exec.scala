package org.enso.projectmanager.infrastructure.execution

import scala.concurrent.Future

/**
  * An executor type class for purely functional effects.
  *
  * @tparam F a monadic context
  */
trait Exec[F[_, _]] {

  /**
    * Executes effectful operation.
    *
    * @param op an effect
    * @tparam E error type
    * @tparam A returned type
    * @return a result of effectfull computation
    */
  def exec[E, A](op: F[E, A]): Future[Either[E, A]]

}

object Exec {

  def apply[F[_, _]](implicit exec: Exec[F]): Exec[F] = exec

}
