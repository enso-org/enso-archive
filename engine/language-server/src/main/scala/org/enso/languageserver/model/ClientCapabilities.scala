package org.enso.languageserver.model

case class ClientCapabilities(
  dynamicRegistration: Boolean = false,
  willSaveWaitUntil: Boolean   = false,
  didSave: Boolean             = false
)
