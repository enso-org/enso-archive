package org.enso.languageserver.io

sealed trait OutputKind

object OutputKind {

  case object StandardOutput extends OutputKind

  case object StandardError extends OutputKind

}
