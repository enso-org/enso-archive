package org.enso.projectmanager.test

import java.time.{OffsetDateTime, ZoneOffset}

import org.enso.projectmanager.infrastructure.time.Clock
import zio.{IO, ZIO}

class ProgrammableClock[R](initialNow: OffsetDateTime)
    extends Clock[ZIO[R, +*, +*]] {

  @volatile
  private var currentTime = initialNow

  override def now(): IO[Nothing, OffsetDateTime] = IO.succeed(currentTime)

  override def nowInUtc(): IO[Nothing, OffsetDateTime] =
    IO.succeed(currentTime.withOffsetSameInstant(ZoneOffset.UTC))

  def moveTimeForward(seconds: Long): Unit =
    currentTime = currentTime.plusSeconds(seconds)

}
