package org.enso.languageserver

object NotificationReceived {

  /** Language server response to [[Notifications.Initialized]]. */
  case object Initialized

  /** Language server response to [[Notifications.Exit]]. */
  case object Exit

}
