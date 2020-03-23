package org.enso.projectmanager.control.effect

import shapeless.=:!=
import zio.ZIO

trait Except[F[+_, +_]] {

  def recover[E, A, B >: A](fa: F[E, A])(
    recovery: PartialFunction[E, B]
  ): F[E, B]

  def recoverWith[E, A, B >: A, E1 >: E](fa: F[E, A])(
    recovery: PartialFunction[E, F[E1, B]]
  ): F[E1, B]

  def liftEither[E, A](either: Either[E, A]): F[E, A]

  def mapError[E, A, E1](fa: F[E, A])(f: E => E1)(
    implicit ev: E =:!= Nothing
  ): F[E1, A]

  def fail[E](error: => E): F[E, Nothing]

  def onError[E, A](fa: F[E, A])(
    cleanUp: PartialFunction[E, F[Nothing, Unit]]
  ): F[E, A]

  def onDie[E, A](fa: F[E, A])(
    cleanUp: PartialFunction[Throwable, F[Nothing, Unit]]
  ): F[E, A]

}

object Except {

  def apply[F[+_, +_]](implicit except: Except[F]): Except[F] = except

  implicit def zioExcept[R]: Except[ZIO[R, +*, +*]] = new ZioExcept[R]

}
