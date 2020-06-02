package org.enso.interpreter.instrument.execution

import org.enso.interpreter.instrument.job.Job

import scala.concurrent.Future

trait JobProcessor {

  def run[A](job: Job[A]): Future[A]

}
