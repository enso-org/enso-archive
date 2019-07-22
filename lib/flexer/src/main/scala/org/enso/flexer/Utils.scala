package org.enso.flexer

object Utils {

  class Wrapper[A](val a: A) {
    def thenDo(sideEffect: => Unit): A = {
      sideEffect
      a
    }
  }

  implicit def wrapper[A](a: A) = new Wrapper(a)
}
