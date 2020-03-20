package org.enso.projectmanager.test

import org.enso.projectmanager.infrastructure.log.Logging
import zio.IO

object NopLogging extends Logging[IO] {
  override def debug(msg: String): IO[Nothing, Unit] = IO.unit

  override def info(msg: String): IO[Nothing, Unit] = IO.unit

  override def error(msg: String): IO[Nothing, Unit] = IO.unit
}
