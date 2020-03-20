package org.enso.projectmanager.infrastructure.random
import java.util.UUID

import zio.IO

object SystemGenerator extends Generator[IO] {
  override def randomUUID(): IO[Nothing, UUID] =
    IO.effectTotal(UUID.randomUUID())
}
