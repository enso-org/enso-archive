package org.enso.projectmanager.infrastructure.random

import java.util.UUID

trait Generator {

  def randomUUID(): UUID

}
