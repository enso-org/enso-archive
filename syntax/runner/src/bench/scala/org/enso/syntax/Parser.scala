package org.enso.syntax

import org.enso.flexer.Macro
import org.enso.parser.Parser
import org.scalameter.api._

import scala.math.pow

object ParserBenchmark extends Bench.OfflineRegressionReport {
  val exp14 = Gen.exponential("size")(pow(2, 11).toInt, pow(2, 14).toInt, 2)
  val exp18 = Gen.exponential("size")(pow(2, 15).toInt, pow(2, 18).toInt, 2)

  val variable   = for { i <- exp18 } yield "abcdefgh" * i
  val text       = for { i <- exp18 } yield "'" + ("abcdefgh" * i) + "'"
  val number     = for { i <- exp18 } yield "12345678" * i
  val calls      = for { i <- exp18 } yield "(a (b " * i + "))" * i
  val codeBlock  = for { i <- exp18 } yield "a=x\nb++\n" * i
  val openParens = for { i <- exp18 } yield "((((((((" * i
  val clsdParens = for { i <- exp18 } yield "((((" * i + "))))" * i
  val allRules   = for { i <- exp14 } yield """
                                            | string = ""ABCD""
                                            | number = 123_4.67
                                            | fib  : Int -> Int
                                            | fib  n = fib n-1 + fib n-2
                                            """.stripMargin * i

  val newParser = Macro.compile(new Parser())

  performance of "parser" in {
    measure method "variable" in (using(variable) in (newParser().run(_)))
    measure method "text" in (using(text) in (newParser().run(_)))
    measure method "number" in (using(number) in (newParser().run(_)))
    measure method "calls" in (using(calls) in (newParser().run(_)))
    measure method "codeBlock" in (using(codeBlock) in (newParser().run(_)))
    measure method "openParens" in (using(openParens) in (newParser().run(_)))
    measure method "clsdParens" in (using(clsdParens) in (newParser().run(_)))
    measure method "allRules" in (using(allRules) in (newParser().run(_)))
  }
}
