package org.enso.interpreter.instrument.execution

import java.util.UUID
import java.util.concurrent.Future

import org.enso.interpreter.instrument.job.Job

case class RunningJob(id: UUID, job: Job[_], future: Future[_])
