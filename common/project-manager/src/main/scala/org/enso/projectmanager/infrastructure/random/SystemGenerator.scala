package org.enso.projectmanager.infrastructure.random
import java.util.UUID

import zio.IO

/**
  * A system pseudo-random numbers generator.
  */
object SystemGenerator extends Generator[IO] {

  /**
    * Returns random UUID in version 4.
    *
    * @return a UUID
    */
  override def randomUUID(): IO[Nothing, UUID] =
    IO.effectTotal(UUID.randomUUID())
}
