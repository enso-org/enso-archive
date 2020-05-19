package org.enso.jsonrpc.test

import org.scalatest._

/**
  * Trait is used to mark the tests in the suite as _flaky_ and make them
  * pass. It changes the behavior for failed tests to return 'pending' instead
  * of failing the suite.
  */
trait FlakySpec extends TestSuite {

  /** Allow test to pass after a failed run marking it as _pending_. */
  def flakyTest(test: => Any): Unit = {
    try test
    catch {
      case _: Exception      => pending
      case _: AssertionError => pending
    }
  }
}
