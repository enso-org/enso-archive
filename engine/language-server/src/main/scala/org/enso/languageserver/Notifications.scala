package org.enso.languageserver

object Notifications {

  /** Akka message sent by Gateway received LSP notification `initialized`. */
  case object Initialized

  /** Akka message sent by Gateway received LSP notification `exit`. */
  case object Exit

}
