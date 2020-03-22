package org.enso.projectmanager.control.core

import zio._

trait CovariantFlatMap[F[+_, +_]] {

  def pure[A](value: A): F[Nothing, A]

  def flatMap[E1, E2 >: E1, A, B](fa: F[E1, A])(f: A => F[E2, B]): F[E2, B]

}

object CovariantFlatMap {

  def apply[F[+_, +_]](
    implicit covariantFlatMap: CovariantFlatMap[F]
  ): CovariantFlatMap[F] =
    covariantFlatMap

  implicit def zioFlatMap[R]
    : CovariantFlatMap[({ type T[+A, +B] = ZIO[R, A, B] })#T] =
    zioFlatMapInstance
      .asInstanceOf[CovariantFlatMap[({ type T[+A, +B] = ZIO[R, A, B] })#T]]

  final private[this] val zioFlatMapInstance
    : CovariantFlatMap[({ type T[+A, +B] = ZIO[Any, A, B] })#T] =
    new ZioCovariantFlatMap[Any]

}
