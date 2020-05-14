package org.enso.languageserver.io

sealed trait OutputKind

object OutputKind {

  case object StdOut extends OutputKind

  case object StdErr extends OutputKind

}
