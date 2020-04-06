package org.enso.languageserver.runtime

import org.enso.languageserver.filemanager.Path

/**
  * An object pointing to a method definition.
  */
case class MethodPointer(file: Path, definedOnType: String, name: String)
