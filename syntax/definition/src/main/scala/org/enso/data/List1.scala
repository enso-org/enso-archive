package org.enso

import cats.data.NonEmptyList

package object data {
  type List1[+T] = NonEmptyList[T]
  object List1 {
    def apply[T](el: T, tail: List[T]): List1[T] = new List1(el, tail)
    def apply[T](el: T, tail: T*):      List1[T] = new List1(el, tail.toList)
  }
}
