package org.enso.projectmanager.service

import zio.IO

trait ProjectValidatorApi {

  def validateName(name: String): IO[ValidationFailure, Unit]

}
