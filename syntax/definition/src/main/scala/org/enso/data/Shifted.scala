package org.enso.data

case class Shifted[+T](off: Int, el: T) {
  def map[S](f: T => S): Shifted[S] =
    Shifted(off, f(el))
}

object Shifted {
  def apply[T](el: T): Shifted[T] = Shifted(0, el)

  case class List1[T](head: T, tail: List[Shifted[T]]) {
    def map[S](f: T => S): List1[S] =
      List1(f(head), tail.map(_.map(f)))

    def toList(): List[Shifted[T]] =
      Shifted(0, head) :: tail

    def +:(t: (Int, T)): List1[T] =
      List1(t._2, Shifted(t._1, head) :: tail)

    def +:(t: Shifted[T]): List1[T] =
      List1(t.el, Shifted(t.off, head) :: tail)

    def +(that: List1[T]): List1[T] =
      List1(head, tail ++ that.toList())

    def +(that: List[Shifted[T]]): List1[T] =
      List1(head, tail ++ that)

    def :+(that: Shifted[T]): List1[T] =
      List1(head, tail :+ that)
  }

  object List1 {
    def apply[T](head: T): List1[T] = List1(head, Nil)
    implicit def fromTuple[T](t: (T, List[Shifted[T]])): List1[T] =
      List1(t._1, t._2)
  }
}
