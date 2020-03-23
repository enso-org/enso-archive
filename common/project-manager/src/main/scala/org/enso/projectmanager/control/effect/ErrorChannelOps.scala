package org.enso.projectmanager.control.effect

import shapeless.=:!=

class ErrorChannelOps[F[+_, +_]: ErrorChannel, E, A](fa: F[E, A]) {

  def recover[B >: A](recovery: PartialFunction[E, B]): F[E, B] =
    ErrorChannel[F].recover[E, A, B](fa)(recovery)

  def recoverWith[B >: A, E1 >: E](
    recovery: PartialFunction[E, F[E1, B]]
  ): F[E1, B] =
    ErrorChannel[F].recoverWith[E, A, B, E1](fa)(recovery)

  def mapError[E1](f: E => E1)(implicit ev: E =:!= Nothing): F[E1, A] =
    ErrorChannel[F].mapError(fa)(f)

  def onError(cleanUp: PartialFunction[E, F[Nothing, Unit]]): F[E, A] =
    ErrorChannel[F].onError(fa)(cleanUp)

  def onDie(cleanUp: PartialFunction[Throwable, F[Nothing, Unit]]): F[E, A] =
    ErrorChannel[F].onDie(fa)(cleanUp)

}
