package org.enso.projectmanager.infrastructure.random
import java.util.UUID

object SystemGenerator extends Generator {
  override def randomUUID(): UUID = UUID.randomUUID()
}
