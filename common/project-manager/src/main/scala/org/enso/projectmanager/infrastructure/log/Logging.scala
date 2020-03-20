package org.enso.projectmanager.infrastructure.log

trait Logging[F[_, _]] {

  def debug(msg: String): F[Nothing, Unit]

  def info(msg: String): F[Nothing, Unit]

  def error(msg: String): F[Nothing, Unit]

}
