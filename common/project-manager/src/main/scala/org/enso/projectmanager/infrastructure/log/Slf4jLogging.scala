package org.enso.projectmanager.infrastructure.log
import com.typesafe.scalalogging.LazyLogging
import zio.IO

object Slf4jLogging extends Logging[IO] with LazyLogging {

  override def debug(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.debug(msg))

  override def info(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.info(msg))

  override def error(msg: String): IO[Nothing, Unit] =
    IO.effectTotal(logger.error(msg))

}
