package org.enso.languageserver.filemanager

/**
  * Signals file system specific errors.
  *
  * @param reason a reason of failure
  */
case class FileSystemFailure(reason: String)
