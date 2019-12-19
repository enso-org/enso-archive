package org.enso.interpreter.bench.fixtures.semantic

import org.enso.interpreter.test.{InterpreterException, InterpreterRunner}
import org.graalvm.polyglot.PolyglotException

import scala.util.Try

class AtomFixtures extends InterpreterRunner {
  val million: Long = 1000000

  val generateListCode =
    """
      |main = length ->
      |  generator = acc i -> ifZero i acc (generator (Cons i acc) (i - 1))
      |
      |  res = generator Nil length
      |  res
    """.stripMargin
  val generateList       = eval(generateListCode, false)
  val millionElementList = generateList.execute(null, million.asInstanceOf[Object])

//  val reverseListCode =
//    """
//      |main = list ->
//      |  reverser = acc list -> case list of
//      |    Cons h t -> reverser (Cons h acc) t
//      |    Nil -> acc
//      |
//      |  res = reverser Nil list
//      |  res
//    """.stripMargin
//  val reverseList = eval(reverseListCode)

  val reverseListMethodsCode =
    """
      |Cons.reverse = acc -> case this of
      |  Cons h t -> reverse t (Cons h acc)
      |
      |Nil.reverse = acc -> acc
      |
      |main = list -> reverse list Nil
      |""".stripMargin
  val reverseListMethods = eval(reverseListMethodsCode, false)
//  println("yyyyyy?")
//  println("hmmmm?")
//  val sumListCode =
//    """
//      |main = list ->
//      |  summator = acc list -> case list of
//      |    Cons h t -> summator acc+h t
//      |    Nil -> acc
//      |
//      |  res = summator 0 list
//      |  res
//    """.stripMargin
//  val sumList = eval(sumListCode)
//
//  val sumListLeftFoldCode =
//    """
//      |main = list ->
//      |  fold = f acc list -> case list of
//      |    Cons h t -> fold f (f acc h) t
//      |    _ -> acc
//      |
//      |  res = fold (x y -> x + y) 0 list
//      |  res
//    """.stripMargin
//  val sumListLeftFold = eval(sumListLeftFoldCode)
//
//  val sumListFallbackCode =
//    """
//      |main = list ->
//      |  summator = acc list -> case list of
//      |    Cons h t -> summator acc+h t
//      |    _ -> acc
//      |
//      |  res = summator 0 list
//      |  res
//    """.stripMargin
//  val sumListFallback = eval(sumListFallbackCode)
//
//  val sumListMethodsCode =
//    """
//      |Nil.sum = acc -> acc
//      |Cons.sum = acc -> case this of
//      |  Cons h t -> sum t h+acc
//      |
//      |main = list -> sum list 0
//      |""".stripMargin
//  val sumListMethods = eval(sumListMethodsCode)
//
//  val mapReverseListCode =
//    """
//      |Nil.mapReverse = f acc -> acc
//      |Cons.mapReverse = f acc -> case this of
//      |  Cons h t -> mapReverse t f (Cons (f h) acc)
//      |
//      |main = list -> mapReverse list (x -> x + 1) Nil
//      |""".stripMargin
//  val mapReverseList = eval(mapReverseListCode)
//
//  val mapReverseListCurryCode =
//    """
//      |Nil.mapReverse = f acc -> acc
//      |Cons.mapReverse = f acc -> case this of
//      |  Cons h t -> mapReverse t f (Cons (f h) acc)
//      |
//      |main = list ->
//      |    adder = x y -> x + y
//      |    mapReverse list (adder 1) Nil
//      |""".stripMargin
//  val mapReverseListCurry = eval(mapReverseListCurryCode)
//  System.out.println(consumeOut)
//  System.out.println(mapReverseListCurry)
}
