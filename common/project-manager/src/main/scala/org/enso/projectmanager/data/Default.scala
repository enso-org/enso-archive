package org.enso.projectmanager.data

trait Default[A] {

  val default: A

}

object Default {

  case class Val[A](override val default: A) extends Default[A]

  def apply[A](implicit default: Default[A]): Default[A] = default

}
