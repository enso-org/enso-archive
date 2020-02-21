package org.enso.languageserver.filemanager

import shapeless.{:+:, CNil}

object failures {

  case class InvalidPath(reason: String)

  case class IoFailure(reason: String)

  type WriteFailure = InvalidPath :+: IoFailure :+: CNil

}
