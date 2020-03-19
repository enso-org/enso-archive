package org.enso.projectmanager.infrastructure.execution

import scala.concurrent.Future

trait Exec[F[_, _]] {

  def exec[E, A](op: F[E, A]): Future[Either[E, A]]

}

object Exec {

  def apply[F[_, _]](implicit exec: Exec[F]): Exec[F] = exec

}
