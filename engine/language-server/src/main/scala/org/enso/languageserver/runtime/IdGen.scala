package org.enso.languageserver.runtime

import java.util.UUID

object IdGen {

  /**
    * Generate fresh id.
    */
  def nextId: UUID =
    UUID.randomUUID()
}
