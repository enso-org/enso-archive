package org.enso.syntax.text

import java.io.File
import java.io.PrintWriter

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.ast.meta.Builtin
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag

import scala.util.Random

////////////////////////////////////////////////////////////////////////////////
//// DocParser /////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

class DocParser {
  import DocParser._
  private val engine = newEngine()

  def runner(input: String, renderHTML: Boolean): AST = run(input) match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) =>
      if (renderHTML) {
        println(v.renderHTML("style.css"))
        val path =
          "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
        saveHTMLCodeToLocalFile(path, v.renderHTML("style.css"))
      }
      v
    case _ => Doc()
  }
  def run(input: String): Result[Doc] = engine.run(new Reader(input))

}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  def runnerWithHTMLRendering(input: String): AST =
    new DocParser().runner(input, renderHTML = true)
  def runner(input: String): AST =
    new DocParser().runner(input, renderHTML = false)
  def run(input: String): Result[Doc] = new DocParser().run(input)

  def saveHTMLCodeToLocalFile(path: String, code: TypedTag[String]): Unit = {
    val writer = new PrintWriter(
      new File(
        path + Random.alphanumeric.take(8).mkString("") + ".html"
      )
    )
    writer.write(code.toString)
    writer.close()
  }
}

object DocParserRunner {
  /* TODO [MM] - CreateDocumentation - walk through parsed data and look in
               Infix for title of function under documentation, then create
               true documented body */

  /** createDocumentation - function for invoking DocParser in right places
    * and creating documentation from parsed comments
    *
    * @param ast - parsed data
    * @return - AST with possible documentation
    */
  def create(ast: AST): AST = {
    ast match {
      case v: AST.Macro.Match        => macroMatchAction(v)
      case v: AST.Comment.MultiLine  => MultilineAction(v)
      case v: AST.Comment.SingleLine => SinglelineAction(v)
      case v: AST.App._Infix         => infixAction(v)
      case v                         => defaultAction(v)
    }
  }

  def SinglelineAction(ast: AST.Comment.SingleLine): AST = {
    println("\n--- FOUND SINGLE LINE COMMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    val in = ast.text
    DocParser.runner(in)
  }

  def MultilineAction(ast: AST.Comment.MultiLine): AST = {
    println("\n--- FOUND MULTI LINE COMMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    val in = ast.lines.mkString("\n")
    DocParser.runner(in)
  }

  def infixAction(ast: AST.App._Infix): AST = {
    println("\n--- FOUND INFIX ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    println("--- L ARG: " + ast.larg)
    println("--- L OFF: " + ast.loff)
    println("--- OPR  : " + ast.opr)
    println("--- R ARG: " + ast.rarg)
    println("--- R OFF: " + ast.roff)
    ast.larg match {
      case _: AST._App =>
        ast.opr match {
          case AST.Opr("=") => createTitleFromInfix(ast)
          case _            => infixActionNoTitleFound(ast)
        }
      case _ => infixActionNoTitleFound(ast)
    }
  }

  def createTitleFromInfix(ast: AST): AST = {
    println("\n--- FOUND LAMBDA DEFINITION ---\n")
    infixActionNoTitleFound(ast) //TODO - CREATE DOC TITLE/HEADER?
  }

  def infixActionNoTitleFound(ast: AST): AST = {
    println("\n--- NO TITLE FOUND ---\n")
    ast.map({ elem =>
      println("\n--- --- --- TRYING WITH ELEM (INFIX): ---\n")
      pprint.pprintln(elem, width = 50, height = 10000)
      create(elem)
    })
  }

  def macroMatchAction(ast: AST.Macro.Match): AST = {
    Builtin.registry.get(ast.path()) match {
      case None => throw new Error("Macro definition not found")
      case Some(spec) =>
        println("\n--- --- --- MATCH FOUND: ---\n")
        pprint.pprintln(spec, width = 50, height = 10000)
        println("\n--- --- --- MATCH FINALIZERS: ---\n")
        pprint.pprintln(
          spec.fin(ast.pfx, ast.segs.toList().map(_.el)),
          width  = 50,
          height = 10000
        )
        create(spec.fin(ast.pfx, ast.segs.toList().map(_.el)))
    }
  }

  def defaultAction(ast: AST): AST = {
    println("\n--- NO COMMENT FOUND IN THIS ELEMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    ast.map({ elem =>
      println("\n--- --- --- TRY FROM ELEM (DEFAULT): ---\n")
      pprint.pprintln(elem, width = 50, height = 10000)
      create(elem)
    })
  }
}
