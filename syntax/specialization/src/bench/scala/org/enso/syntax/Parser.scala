package org.enso.syntax

import org.enso.flexer.ParserReader
import org.enso.syntax.text.Parser
import org.scalameter.api._

import scala.math.pow

object ParserBenchmark extends Bench.OfflineRegressionReport {

  val range = 0
  def exp(i: Int) =
    Gen.exponential("size")(pow(2, i - range).toInt, pow(2, i).toInt, 2)

  def gen(range: Gen[Int], f: Int => String): Gen[String] =
    for { i <- range } yield f(i)

  val tests = List(
    "variable"   -> gen(exp(18), i => "abcdefgh" * i),
    "text"       -> gen(exp(18), i => "'" + ("abcdefgh" * i) + "'"),
    "number"     -> gen(exp(18), i => "12345678" * i),
    "calls"      -> gen(exp(11), i => "(a (b " * i + "))" * i),
    "codeBlock"  -> gen(exp(18), i => "a=x\nb++\n" * i),
    "openParens" -> gen(exp(18), i => "((((((((" * i),
    "clsdParens" -> gen(exp(18), i => "((((" * i + "))))" * i),
    "allRules" -> gen(
      exp(14),
      i => """
             | string = ""ABCD""
             | number = 123_4.67
             | fib  : Int -> Int
             | fib  n = fib n-1 + fib n-2
           """.stripMargin * i
    )
  )

  def run(str: String) = Parser().run(new ParserReader(str))
  performance of "parser" in {
    tests.foreach {
      case (name, gen) => measure method name in (using(gen) in run)
    }
  }
}
