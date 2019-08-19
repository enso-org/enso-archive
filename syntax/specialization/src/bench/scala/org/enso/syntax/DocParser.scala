package org.enso.syntax

import org.enso.syntax.text.DocParser
import org.scalameter.api._

import scala.math.pow

object DocParserBenchmark extends Bench.OfflineRegressionReport {
  val range = 0
  val part =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi sodales mi vitae orci imperdiet amet" // 100 letters
  val tests = List(
    "formatters" -> gen(exp(8), i => "*A* ~B~ _C_ *~_D_~*" * i),
    "longS"      -> gen(exp(8), i => "test12" * i),
    "100ls"      -> gen(exp(8), i => (part + "\n") * i),
    "GinS"       -> gen(exp(8), i => part * i),
    "tags"       -> gen(exp(8), i => "DEPRECATED\nMODIFIED\nADDED in 1.9" * i),
    "url"        -> gen(exp(8), i => "[link](http://foo.com)" * i),
    "image"      -> gen(exp(8), i => "![Image](http://foo.jpg)" * i),
    "list" -> gen(
      exp(8),
      i => """List
             |  - A
             |  - B
             |  - C""".stripMargin * i
    ),
    "listNT" -> gen(
      exp(8),
      i => """List
             |  - A
             |  - B
             |    * CA
             |    * CB
             |  - D""".stripMargin * i
    ),
    "sections" -> gen(exp(8), i => "Foo \n\nA \n\n ! B\n\n ? C \n\n > D " * i)
  )

  def exp(i: Int) =
    Gen.exponential("size")(pow(2, i - range).toInt, pow(2, i).toInt, 2)

  def gen(range: Gen[Int], f: Int => String): Gen[String] =
    for { i <- range } yield f(i)

  def run(str: String) = DocParser.run(str)
  performance of "DocParser" in {
    tests.foreach {
      case (name, gen) => measure method name in (using(gen) in run)
    }
  }
}
