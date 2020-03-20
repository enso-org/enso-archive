package org.enso.projectmanager.test

import java.util.UUID

import org.enso.projectmanager.infrastructure.random.Generator

class TestGenerator(testUUID: UUID) extends Generator {
  override def randomUUID(): UUID = testUUID
}
