package org.enso.projectmanager.control.effect

object syntax {

  implicit def toSyncOps[F[+_, +_]: Sync, E, A](fa: F[E, A]): SyncOps[F, E, A] =
    new SyncOps[F, E, A](fa)

  implicit def toErrorChannelOps[F[+_, +_]: ErrorChannel, E, A](
    fa: F[E, A]
  ): ErrorChannelOps[F, E, A] =
    new ErrorChannelOps[F, E, A](fa)

}
