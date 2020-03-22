package org.enso.projectmanager.infrastructure.random
import java.util.UUID

import zio.{IO, ZIO}

/**
  * A system pseudo-random numbers generator.
  */
class SystemGenerator[R] extends Generator[ZIO[R, *, *]] {

  /**
    * Returns random UUID in version 4.
    *
    * @return a UUID
    */
  override def randomUUID(): IO[Nothing, UUID] =
    IO.effectTotal(UUID.randomUUID())
}
