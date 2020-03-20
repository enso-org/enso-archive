package org.enso.projectmanager.service

trait ProjectValidator[F[_, _]] {

  def validateName(name: String): F[ValidationFailure, Unit]

}
