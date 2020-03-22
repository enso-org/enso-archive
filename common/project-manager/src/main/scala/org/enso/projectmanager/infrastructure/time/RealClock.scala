package org.enso.projectmanager.infrastructure.time
import java.time.{OffsetDateTime, ZoneOffset}

import zio.IO

/**
  * A system clock.
  */
object RealClock extends Clock[IO] {

  /**
    * Obtains the current date-time from the system clock in the default time-zone.
    *
    * @return a date time
    */
  override def now(): IO[Nothing, OffsetDateTime] =
    IO.effectTotal(OffsetDateTime.now())

  /**
    * Obtains the current date-time from the system clock in the UTC time-zone.
    *
    * @return a date time
    */
  override def nowInUtc(): IO[Nothing, OffsetDateTime] =
    IO.effectTotal(OffsetDateTime.now(ZoneOffset.UTC))

}
