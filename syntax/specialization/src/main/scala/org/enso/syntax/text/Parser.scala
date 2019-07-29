package org.enso.syntax.text

import org.enso.flexer
import org.enso.flexer.Macro.compile
import org.enso.syntax.text
import org.enso.syntax.text.precedence.Mixfix

////////////////
//// Parser ////
////////////////

class Parser {
  import Parser._
  private val engine = newEngine()

  def run(input: String): Result[AST.Module] = engine.run(input).map { module =>
    val module2 = module.asInstanceOf[AST.Module] // FIXME
    Mixfix.run(module2)
  }

}

object Parser {
  type Result[T] = flexer.Result[T]
  private val newEngine = compile(text.ParserDef)
}

//////////////
//// Main ////
//////////////

object Main extends App {
  val p1 = new Parser()
  val p2 = new Parser()

//  val out = p1.run("t   if  a+b   *    c     then      x")
//val out = p1.run("if (a then")
  val out = p1.run("import Std.Math")
  //  val out = p1.run("(a")
//  val out = p1.run("()")
//  val out = p1.run("if a then x")
  pprint.pprintln(out, width = 50, height = 10000)

  DocParser

//  val out = p1.run("(t  if a then b)")
//  val out = p1.run("x ( a  b   )")

//  out match {
//    case Success(v, _) =>
//      pprint(v)
//      println(v.show())
//  }
  println()
}
