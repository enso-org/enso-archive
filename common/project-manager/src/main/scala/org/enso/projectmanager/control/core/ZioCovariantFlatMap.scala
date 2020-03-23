package org.enso.projectmanager.control.core

import zio.ZIO

private[core] class ZioCovariantFlatMap[R]
    extends CovariantFlatMap[ZIO[R, +*, +*]] {

  override def pure[A](value: A): ZIO[R, Nothing, A] = ZIO.succeed(value)

  override def flatMap[E1, E2 >: E1, A, B](
    fa: ZIO[R, E1, A]
  )(f: A => ZIO[R, E2, B]): ZIO[R, E2, B] =
    fa.flatMap(f)
}
