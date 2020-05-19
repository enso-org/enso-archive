package org.enso.jsonrpc.test

import org.scalatest._

/**
  * Trait is used to mark the tests in the suite as _flaky_ and make them
  * pass. It changes the behavior for failed tests to return 'pending' instead
  * of failing the suite.
  */
trait FlakySpec extends TestSuite {

  /** Tests that will pass even after a failed run. */
  def FLAKY_TESTS: Seq[String]

  override def withFixture(test: NoArgTest): Outcome =
    super.withFixture(test) match {
      case Failed(_) | Canceled(_) if FLAKY_TESTS.exists(test.name.contains) =>
        Pending
      case outcome =>
        outcome
    }
}
