package org.enso.languageserver

object Notifications {

  /** Akka message sent by Gateway received LSP notification `initialized`. */
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
