package org.enso.projectmanager.control.effect

import scala.concurrent.Future
import scala.util.Either

trait Async[F[+_, +_]] {

  def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]

  def fromFuture[A](thunk: () => Future[A]): F[Throwable, A]

}

object Async {

  def apply[F[+_, +_]](implicit async: Async[F]): Async[F] = async

  implicit def zioAsync[R]: ZioAsync[R] = new ZioAsync[R]

}
