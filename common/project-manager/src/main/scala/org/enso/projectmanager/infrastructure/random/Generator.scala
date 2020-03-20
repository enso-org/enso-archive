package org.enso.projectmanager.infrastructure.random

import java.util.UUID

trait Generator[F[_, _]] {

  def randomUUID(): F[Nothing, UUID]

}
