package org.enso.projectmanager.control.effect

object syntax {

  implicit def toSyncOps[F[+_, +_]: Sync, E, A](fa: F[E, A]): SyncOps[F, E, A] =
    new SyncOps[F, E, A](fa)

  implicit def toExceptOps[F[+_, +_]: Except, E, A](
    fa: F[E, A]
  ): ExceptOps[F, E, A] =
    new ExceptOps[F, E, A](fa)

}
