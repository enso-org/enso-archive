package org.enso.syntax.text

import org.enso.flexer
import org.enso.syntax.text
import org.enso.syntax.text.AST.Marker
import org.enso.syntax.text.precedence.Template

////////////////
//// Parser ////
////////////////

class Parser {
  import Parser._
  private val engine = newEngine()

  def run(input: String, mrkr: Seq[(Int, Marker)] = Seq()): Result[AST.Module] =
    engine.run(input, mrkr).map { module: AST =>
      val module2 = module.asInstanceOf[AST.Module] // FIXME
      Template.run(module2)
    }

}

object Parser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(text.ParserDef)

  def apply(): Parser = new Parser()
}

//////////////
//// Main ////
//////////////

object Main extends App {
  val p1 = new Parser()
  val p2 = new Parser()

  val inp = "import Std.Math"
  val out = p1.run(inp, Seq())
  pprint.pprintln(out, width = 50, height = 10000)

  out match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) =>
      v.lines.head.elem match {
        case Some(ast) =>
          ast match {
            case t: AST.Template.Valid =>
              println("\n---\n")
              Template.hardcodedRegistry.get(t.path()) match {
                case None => println(":(")
                case Some(spec) =>
                  println("COMPUTING")
                  val out = spec.finalizer(t.segments.toList)
                  println(out)
              }
            case _ =>
          }
        case _ =>
      }

      println(v.show() == inp)
      println("------")
      println(v.show().replace(' ', '.'))
      println("------")

  }
  println()
}
