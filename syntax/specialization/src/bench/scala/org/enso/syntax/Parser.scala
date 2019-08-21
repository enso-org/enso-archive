package org.enso.syntax

import java.io.{BufferedInputStream, BufferedReader, DataInputStream, File, FileInputStream, FileReader, StringReader}

import org.enso.syntax.text.Parser
import org.scalameter.api._
import org.enso.flexer

import scala.math.pow

object ParserBenchmark extends Bench.LocalTime {

  val range = 0
  def exp(i: Int) =
    Gen.exponential("size")(pow(2, i - range).toInt, pow(2, i).toInt, 2)

  def gen(range: Gen[Int], f: Int => String): Gen[String] =
    for { i <- range } yield f(i)

  val tests = List(
    "text"       -> gen(exp(18), i => "'abcdefgh'" * i),
    "number"     -> gen(exp(18), i => "12345678 " * i),
    "calls"      -> gen(exp(11), i => "(a b " * i + ")" * i),
    "codeBlock"  -> gen(exp(18), i => "a=x\nb++\n" * i),
    "openParens" -> gen(exp(18), i => "((((((((" * i),
    "clsdParens" -> gen(exp(18), i => "((((" * i + "))))" * i),
    "allRules" -> gen(
      exp(14),
      i => """
             | string = "ABCD"
             | number = 123_4.67
             | fib   : Int -> Int
             | fib n = fib n-1 + fib n-2
             |""".stripMargin * i
    )
  )

  def run(str: String) = Parser().run(new flexer.Reader(str))

  performance of "parser" in {
    tests.foreach {
      case (name, gen) => measure method name in (using(gen) in run)
    }
  }

  val dummy = for { i <- exp(0) } yield i

  val filename = "syntax/specialization/src/bench/scala/org/enso/syntax/input.txt"
  def runFlexerReader() = new flexer.ReaderUTF(new File(filename)).toString()
  def runBufferedReader() = {
    val reader  = new BufferedReader(new FileReader(filename))
    val builder = new java.lang.StringBuilder()
    var char    = 0
    while ({ char = reader.read(); char != -1 }) {
      builder.append(char.toChar)
      if (char.toChar.isHighSurrogate)
        builder.append(reader.read().toChar)
    }
    builder.toString
  }

  performance of "reader" in {
      measure method s"Buffered" in { using(dummy) in (_ => runBufferedReader()) }
      measure method s"Flexer" in { using(dummy) in (_ => runFlexerReader()) }
  }
}
