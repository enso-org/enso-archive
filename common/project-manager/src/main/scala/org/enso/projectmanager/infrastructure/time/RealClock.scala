package org.enso.projectmanager.infrastructure.time
import java.time.{OffsetDateTime, ZoneOffset}

import zio.IO

object RealClock extends Clock {

  override def now(): IO[Nothing, OffsetDateTime] =
    IO.effectTotal(OffsetDateTime.now())

  override def nowInUtc(): IO[Nothing, OffsetDateTime] =
    IO.effectTotal(OffsetDateTime.now(ZoneOffset.UTC))

}
