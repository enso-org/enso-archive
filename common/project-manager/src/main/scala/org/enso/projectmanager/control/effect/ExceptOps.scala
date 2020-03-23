package org.enso.projectmanager.control.effect

import shapeless.=:!=

class ExceptOps[F[+_, +_]: Except, E, A](fa: F[E, A]) {

  def recover[B >: A](recovery: PartialFunction[E, B]): F[E, B] =
    Except[F].recover[E, A, B](fa)(recovery)

  def recoverWith[B >: A, E1 >: E](
    recovery: PartialFunction[E, F[E1, B]]
  ): F[E1, B] =
    Except[F].recoverWith[E, A, B, E1](fa)(recovery)

  def mapError[E1](f: E => E1)(implicit ev: E =:!= Nothing): F[E1, A] =
    Except[F].mapError(fa)(f)

}
