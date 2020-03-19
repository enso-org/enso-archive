package org.enso.projectmanager.infrastructure.time

import java.time.OffsetDateTime

import zio.IO

trait Clock {

  def now(): IO[Nothing, OffsetDateTime]

  def nowInUtc(): IO[Nothing, OffsetDateTime]

}
