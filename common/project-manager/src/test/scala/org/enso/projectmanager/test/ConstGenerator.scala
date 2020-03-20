package org.enso.projectmanager.test

import java.util.UUID

import org.enso.projectmanager.infrastructure.random.Generator
import zio.IO

class ConstGenerator(testUUID: UUID) extends Generator[IO] {
  override def randomUUID(): IO[Nothing, UUID] = IO.succeed(testUUID)
}
