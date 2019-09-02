//package org.enso.syntax
//
//import java.io.BufferedReader
//import java.io.File
//import java.io.FileReader
//import java.io.PrintWriter
//import org.enso.syntax.text.AST
//
//import org.enso.flexer
//import org.enso.syntax.text.Parser
//import org.scalameter.api._
//import org.enso.syntax.text.AST.implicits._
//
//import scala.math.pow
//
//object ParserBenchmark extends Bench.LocalTime {
//
//  val range = 5
//  def exp(i: Int) =
//    Gen.exponential("size")(pow(2, i - range).toInt, pow(2, i).toInt, 2)
//
//  def gen(range: Gen[Int], f: Int => Int): Gen[Int] =
//    for { i <- range } yield f(i)
//
//  def testAST0(i: Int): Unit = {
//    val n1     = "foo" + i.toString
//    val n2     = n1 + "!"
//    var v: AST = AST.Var(n1)
//    for { j <- 0.to(i) } {
//      if (j % 2 == 0) {
//        v = AST.App.Prefix(AST.Var(n2), 1, v)
//      } else {
//        v = AST.App.Prefix(AST.Var(n1), 2, v)
//      }
//    }
//    var x: Int = 0
//    for { j <- 0.to(i) } {
//      v = v match {
//        case AST.Var(_, _) =>
//          x -= 1
//          v
//        case AST._App(_, off, arg, _) => {
//          x += off
//          arg
//        }
//      }
//    }
//  }
//
//  def testAST1(i: Int): Unit = {
//    val n1     = "foo" + i.toString
//    val n2     = n1 + "!"
//    var v: AST = AST.Var(n1)
//    for { j <- 0.to(i) } {
//      v = AST.App(AST.Var(n2), 1, v)
//    }
//    var x: Int = 0
//    for { j <- 0.to(i) } {
//      v = v match {
//        case t: AST.App => {
//          x += t.off
//          t.arg
//        }
//      }
//    }
//  }
////
////  def testAST2(i: Int): Unit = {
////    val n1              = "foo" + i.toString
////    val n2              = n1 + "!"
////    var v: ast2.AST.AST = ast2.AST.Var(n1)
////    for { j <- 0.to(i) } {
////      v = ast2.AST.App.Prefix(ast2.AST.Var(n2), 1, v)
////    }
////    var x: Int = 0
////    for { j <- 0.to(i) } {
////      v = v.unFix.shape match {
////        case ast2.AST.App.PrefixOf(fn, off, arg) => {
////          x += off
////          arg
////        }
////      }
////    }
////    //    println(x)
////  }
////
////  def testAST2x(i: Int): Unit = {
////    val n1              = "foo" + i.toString
////    val n2              = n1 + "!"
////    var v: ast2.AST.AST = ast2.AST.Var(n1)
//////    val vv              = ast2.AST.App.Prefix(v, 1, v)
//////    val vv2: ast2.AST.AST =
//////      ast2.AST.Tagged(vv)(ast2.AST.App.implicits.reprPrefix)
//////    val vv2: ast2.AST.AST = ast2. vv
////    for { j <- 0.to(i) } {
////      v = ast2.AST.Tagged(ast2.AST.App.Prefix(ast2.AST.Var(n2), 1, v))(
////        ast2.AST.App.implicits.reprPrefix
////      )
////    }
////    var x: Int = 0
////    for { j <- 0.to(i) } {
////      v = v.unFix.shape match {
////        case ast2.AST.App.PrefixOf(fn, off, arg) => {
////          x += off
////          arg
////        }
////      }
////    }
//////    println(x)
////  }
//
//  def testAST5(i: Int): Unit = {
//    val n1              = "foo" + i.toString
//    val n2              = n1 + "!"
//    var v: ast5.AST.AST = ast5.AST.Var(n1)
//    for { j <- 0.to(i) } {
//      v = ast5.AST.App.Prefix(ast5.AST.Var(n2), 1, v)
//    }
//    var x: Int = 0
//    for { j <- 0.to(i) } {
//      v = v.unFix.shape match {
//        case ast5.AST.App.PrefixOf(fn, off, arg) => {
//          x += off
//          arg
//        }
//      }
//    }
////        println(x)
//  }
//
//  def testAST5_2(i: Int): Unit = {
//    val n1              = "foo" + i.toString
//    val n2              = n1 + "!"
//    var v: ast5.AST.AST = ast5.AST.Var(n1)
//    for { j <- 0.to(i) } {
//      v = ast5.AST.App.Prefix(ast5.AST.Var(n2), 1, v)
//    }
//    var x: Int = 0
//    for { j <- 0.to(i) } {
//      v = v match {
//        case ast5.AST.App.Prefix.any(t) => {
//          x += t.shape.off
//          t.shape.arg
//        }
//        case _ =>
//          println(":(")
//          v
//      }
//    }
//    //    println(x)
//  }
//
////  def testAST3(i: Int): Unit = {
////    val n1              = "foo" + i.toString
////    val n2              = n1 + "!"
////    var v: ast2.AST.AST = ast2.AST.Var(n1)
////    for { j <- 0.to(i) } {
////      v = ast2.AST.App.Prefix(ast2.AST.Var(n2), 1, v)
////    }
////    var x: Int = 0
////    for { j <- 0.to(i) } {
////      v = v.unFix.shape match {
////        case ast2.AST.App.PrefixOf(fn, off, arg) => {
////          x += off
////          arg
////        }
////      }
////    }
////    //    println("y")
////  }
//
//  val tests = List(
////    "text"     -> gen(exp(14), i => "'abcdefgh'" * i),
////    "number"   -> gen(exp(14), i => "12345678 " * i),
//    "testAST1" -> gen(exp(19), i => i)
////    "calls"      -> gen(exp(11), i => "(a b " * i + ")" * i),
////    "codeBlock"  -> gen(exp(18), i => "a=x\nb++\n" * i),
////    "openParens" -> gen(exp(18), i => "((((((((" * i),
////    "clsdParens" -> gen(exp(18), i => "((((" * i + "))))" * i),
////    "allRules" -> gen(
////      exp(14),
////      i => """
////             | string = "ABCD"
////             | number = 123_4.67
////             | fib   : Int -> Int
////             | fib n = fib n-1 + fib n-2
////             |""".stripMargin * i
////    )
//  )
//
//  def run(str: String) = Parser().run(new flexer.Reader(str))
////
////  performance of "testAST0" in {
////    tests.foreach {
////      case (name, gen) => measure method name in (using(gen) in testAST0)
////    }
////  }
////
//  performance of "testAST1" in {
//    tests.foreach {
//      case (name, gen) => measure method name in (using(gen) in testAST1)
//    }
//  }
////
//////  performance of "testAST2" in {
//////    tests.foreach {
//////      case (name, gen) => measure method name in (using(gen) in testAST2)
//////    }
//////  }
//////
//////  performance of "testAST2x" in {
//////    tests.foreach {
//////      case (name, gen) => measure method name in (using(gen) in testAST2)
//////    }
//////  }
////
//  performance of "testAST5" in {
//    tests.foreach {
//      case (name, gen) => measure method name in (using(gen) in testAST5)
//    }
//  }
//  performance of "testAST5_2" in {
//    tests.foreach {
//      case (name, gen) => measure method name in (using(gen) in testAST5_2)
//    }
//  }
//
////  val dummy = for { i <- exp(0) } yield i
////
////  val filename = "syntax/specialization/target/bench-input.txt"
////
////  def runReader()    = new flexer.Reader(new File(filename)).toString()
////  def runReaderUTF() = new flexer.ReaderUTF(new File(filename)).toString()
////  def runBufferedReader() = {
////    val reader  = new BufferedReader(new FileReader(filename))
////    val builder = new java.lang.StringBuilder()
////    var char    = 0
////    while ({ char = reader.read(); char != -1 }) {
////      builder.append(char.toChar)
////      if (char.toChar.isHighSurrogate)
////        builder.append(reader.read().toChar)
////    }
////    builder.toString
////  }
////
////  if (!new File(filename).exists()) {
////    val file = new PrintWriter(new File(filename))
////    for (_ <- 1 to 10000) {
////      file.print("rewuipf\uD800\uDF1Edsahkjlzcvxm,/\uD800\uDF1E.m,';k]o1&**&$")
////      file.print("6!@#&*()+|{}QWERYUI\uD800\uDF1EOIO\uD800\uDF1E}ASFDJKM>>?\n")
////    }
////  }
////
////  performance of "reader" in {
////    measure method "Buffered" in { using(dummy) in (_ => runBufferedReader()) }
////    measure method "FlexerUTF" in { using(dummy) in (_ => runReaderUTF()) }
////    measure method "Flexer" in { using(dummy) in (_ => runReader()) }
////  }
//}
