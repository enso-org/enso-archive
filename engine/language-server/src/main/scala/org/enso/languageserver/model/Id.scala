package org.enso.languageserver.model

/** Id of [[org.enso.languageserver.Request]],
  * [[org.enso.languageserver.Response]].
  */
sealed trait Id

object Id {

  /** A number id. */
  case class Number(value: Int) extends Id

  /** A string id. */
  case class Text(value: String) extends Id

}
