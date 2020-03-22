package org.enso.projectmanager.infrastructure.log
import com.typesafe.scalalogging.LazyLogging
import zio.{IO, ZIO}

/**
  * Slf4j logging interpreter.
  */
class Slf4jLogging[R] extends Logging[ZIO[R, *, *]] with LazyLogging {

  override def debug(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.debug(msg))

  override def info(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.info(msg))

  override def error(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.error(msg))

}
