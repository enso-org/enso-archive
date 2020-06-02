package org.enso.interpreter.instrument.execution

import java.util.UUID

trait JobControlPlane {

  def abortAllJobs(): Unit

  def abortJobs(contextId: UUID): Unit

}
