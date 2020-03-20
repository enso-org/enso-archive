package org.enso.projectmanager.infrastructure.time

import java.time.OffsetDateTime

trait Clock[F[_, _]] {

  def now(): F[Nothing, OffsetDateTime]

  def nowInUtc(): F[Nothing, OffsetDateTime]

}
