package org.enso.syntax

import org.enso.syntax.text.DocParser
import org.enso.syntax.text.DocParser.Result
import org.enso.syntax.text.ast.Doc
import org.scalameter.api._

import scala.math.pow

object DocParserBench extends Bench.LocalTime {

  val range = 0
  def exp(i: Int): Gen[Int] =
    Gen.exponential("size")(pow(2, i - range).toInt, pow(2, i).toInt, 2)

  def gen(range: Gen[Int], f: Int => String): Gen[String] =
    for { i <- range } yield f(i)

  val tests = List(
    "formatters" -> gen(exp(18), i => "*foobar*\n" * i),
    "unclosed"   -> gen(exp(18), i => "*_foobo*\n" * i),
    "combined"   -> gen(exp(18), i => "*_~fo~_*\n" * i),
    "normal"     -> gen(exp(18), i => "test1234\n" * i),
    "tags"       -> gen(exp(18), i => "ADDED\nfoo" * i),
    "link"       -> gen(exp(18), i => "[fo](bo)\n" * i),
    "list" -> gen(
      exp(18),
      i => """foo
             |  - A
             |  - B
             |  - C
             |""".stripMargin * i
    ),
    "list_nested" -> gen(
      exp(18),
      i => """foo
             |  - A
             |  - B
             |    * CA
             |    * CB
             |  - D
             |""".stripMargin * i
    ),
    "sections" -> gen(
      exp(18),
      i => "Foo\n\n!B\n\n?C\n\n>D" * i
    )
  )

  def run(str: String): Result[Doc] = DocParser.run(str)
  performance of "DocParser" in {
    tests.foreach {
      case (name, gen) => measure method name in (using(gen) in run)
    }
  }
}
