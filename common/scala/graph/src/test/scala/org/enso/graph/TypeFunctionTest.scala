package org.enso.graph

import shapeless.{::, HNil}
import shapeless.Nat._

import scala.collection.mutable

object TypeFunctionTest {

  object HListSumTest {
    implicitly[HListSum.Aux[HNil, _0]]
    implicitly[HListSum.Aux[_1 :: HNil, _1]]
    implicitly[HListSum.Aux[_1 :: _2 :: HNil, _3]]
    implicitly[HListSum.Aux[_1 :: _2 :: _3 :: HNil, _6]]
  }

  object HListTakeUntilTest {
    case class A()
    case class B()
    case class C()

    implicitly[HListTakeUntil.Aux[A, HNil, HNil]]
    implicitly[HListTakeUntil.Aux[A, A :: B :: C :: HNil, HNil]]
    implicitly[HListTakeUntil.Aux[B, A :: B :: C :: HNil, A :: HNil]]
    implicitly[HListTakeUntil.Aux[C, A :: B :: C :: HNil, A :: B :: HNil]]
  }

  object MapSizedTest {
    case class A()
    case class B()
    case class C()
    implicit def sizedA = new Sized[A] { type Out = _1 }
    implicit def sizedB = new Sized[B] { type Out = _3 }
    implicit def sizedC = new Sized[C] { type Out = _5 }

    implicitly[MapSized.Aux[HNil, HNil]]
    implicitly[MapSized.Aux[A :: B :: C :: HNil, _1 :: _3 :: _5 :: HNil]]
  }

  object SizeUntilTest {
    case class A()
    case class B()
    case class C()
    implicit def sizedA = new Sized[A] { type Out = _1 }
    implicit def sizedB = new Sized[B] { type Out = _3 }
    implicit def sizedC = new Sized[C] { type Out = _5 }

    implicitly[SizeUntil.Aux[A, HNil, _0]]
    implicitly[SizeUntil.Aux[A, A :: B :: C :: HNil, _0]]
    implicitly[SizeUntil.Aux[B, A :: B :: C :: HNil, _1]]
    implicitly[SizeUntil.Aux[C, A :: B :: C :: HNil, _4]]
  }

  object MapsOfTest {
    case class A()
    case class B()
    case class C()

    implicitly[MapsOf.Aux[HNil, HNil]]
    implicitly[
      MapsOf.Aux[Double :: HNil, (Double, mutable.Map[Int, Double]) :: HNil]
    ]
    implicitly[MapsOf.Aux[
      String :: Double :: HNil,
      (String, mutable.Map[Int, String])
      :: (Double, mutable.Map[Int, Double]) :: HNil
    ]]
  }
}
