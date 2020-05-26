package org.enso.interpreter.instrument.execution

import org.enso.interpreter.instrument.job.Job

import scala.concurrent.Future

trait JobProcessor {

  def run(job: Job): Future[Done.type]

}
