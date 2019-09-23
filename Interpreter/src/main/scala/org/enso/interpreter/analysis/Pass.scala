package org.enso.interpreter.analysis

import shapeless.HNil
import shapeless._

/**
  * A [[Pass]] is a representation of a transformation from an input format to an output format.
  *
  * A given pass can be a singular transformation, or can be a name given to a whole collection of
  * transformations to be made. This means that a given pass `ConstantFold` can be an aggregate of
  *
  * The invariants that this system needs to preserve are as follows.
  *
  * - Each [[Pass]] has an input type `TIn`, and an output type `TOut`.
  * - Each [[Pass]] has a `val dependsOn: Set[P <: Pass]` that contains all the passes that must be
  *   executed before it.
  * - Given an ordered `Seq[P <: Pass]`, such as `[P1, P2, P3, ...]`, ensure that no `PN` depends on
  *   some `PJ` such that `J >= N`. This ensures that the pass ordering is well-formed.
  * - For a given `PN`, it must hold that `P(N-1).TOut == PN.TIn` and `P(N+1).TIn == PN.TOut`.
  *
  * Consider, for a moment, the type of HLists: `::[A, B]`. In this context, we can produce a
  * heterogeneous list of types, but the problem is instantiating them. However, if we use a
  * type-bound, we can guarantee that all members of the list have a given method:
  *
  * {{{
  *   sealed trait ::[A <: Pass, B <: Pass]
  * }}}
  *
  *
  * PassSeq[Q], empty :: PassSeq[Unit], addPass :: PassSeq[Q] -> Pass[Q, R] -> PassSeq[R]
  * compose :: PassSeq[Q] -> Pass[Unit, Q]
  */
trait Pass {
  type TIn
  type TOut

  def runPass(input: TIn): TOut
}

class PassOne extends Pass {
  override type TIn  = Double
  override type TOut = Int

  override def runPass(input: Double): Int = ???
}
class PassTwo extends Pass {
  override type TIn  = Int
  override type TOut = Long

  override def runPass(input: Int): Long = ???
}
class PassThree extends Pass {
  override type TIn  = Long
  override type TOut = Double

  override def runPass(input: Long): Double = ???
}

trait Foo {
  type list = PassOne :: PassTwo :: PassThree :: HNil

  val one: PassOne     = new PassOne
  val two: PassTwo     = new PassTwo
  val three: PassThree = new PassThree

  def restrictedCons[A <: Pass, B <: Pass](a: A, b: B :: HList)(
    implicit evidence: a.TOut =:= b.head.TIn
  ): HList = a :: b

  // TODO [AA] Will need to wrap HList I think
  // TODO [AA] fromList method
//  val test: HList = restrictedCons(one, HNil)

  type isValidPassList[X] <: X
}

trait RunAnalyser {}

// TODO [AA] Provide utilities for combining passes in a well-typed fashion.

sealed trait MyBool {
  type Not <: MyBool
  type &&[That <: MyBool] <: MyBool
  type ||[That <: MyBool] <: MyBool
}
sealed trait MyTrue extends MyBool {
  override type Not = MyFalse
  override type ||[That <: MyBool] = MyTrue
  override type &&[That <: MyBool] = That
}
sealed trait MyFalse extends MyBool {
  override type Not = MyTrue
  override type ||[That <: MyBool] = MyBool
  override type &&[That <: MyBool] = MyFalse
}

object MyBoolSpecs {
  implicitly[MyTrue =:= MyTrue]
  implicitly[MyFalse =:= MyFalse]

  implicitly[MyTrue#Not =:= MyFalse]
  implicitly[MyFalse#Not =:= MyTrue]

  implicitly[(MyTrue# || [MyFalse]) =:= MyTrue]
}
