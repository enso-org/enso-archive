package org.enso.projectmanager.infrastructure.log

import zio.IO

trait Logging {

  def debug(msg: String): IO[Nothing, Unit]

  def info(msg: String): IO[Nothing, Unit]

  def error(msg: String): IO[Nothing, Unit]

}
