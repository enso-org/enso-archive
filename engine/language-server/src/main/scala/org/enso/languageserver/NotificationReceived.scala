package org.enso.languageserver

object NotificationReceived {

  /** Language server response to [[Initialized]]. */
  case object Initialized

  /** */
  case object Exit

  /** */
  case object DidOpenTextDocument

  /** */
  case object DidChangeTextDocument

  /** */
  case object DidSaveTextDocument

  /** */
  case object DidCloseTextDocument

}
