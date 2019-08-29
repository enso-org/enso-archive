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
//// Doc Parser ////////////////////////////////////////////////////////////////
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

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser Runner /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

object DocParserRunner {
  /* TODO [MM] - CreateDocumentation - walk through parsed data and look in
               Infix for title of function under documentation, then create
               true documented body */

  var previousElement: AST = AST.Blank
  var prevDoc: Doc         = Doc()

  /** create- function for invoking DocParser in right places
    * and creating documentation from parsed comments
    *
    * @param ast - parsed data
    * @return - AST with possible documentation
    */
  def create(ast: AST): AST = {
    var astParsed = findCommentsAndTitles(ast)
    astParsed = loopToOrientDocs(astParsed)
    astParsed
  }

  def loopToOrientDocs(ast: AST): AST = {
    ast match {
      case v: Doc =>
        println("DOC FOUND! : " + v + "\n")
        if (prevDoc == Doc()) {
          prevDoc = v
          Doc()
        } else {
          val head = AST.Block._Line(Some(v), 0)
          val body = AST.Block._Line(Some(prevDoc), 0)
          prevDoc = Doc()

          AST.Module(head, body)
        }
      case v =>
        println("NO DOC : " + v + "\n")
        v.map({ elem =>
          println("LOOPING : " + elem + "\n")
          loopToOrientDocs(elem)
        })
    }
  }

  def findCommentsAndTitles(ast: AST): AST = {
    println("PREVIOUS ELEMENT: " + previousElement + "--- --- --- --- --- ---")
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
    previousElement = DocParser.runner(in)
    previousElement
  }

  def MultilineAction(ast: AST.Comment.MultiLine): AST = {
    println("\n--- FOUND MULTI LINE COMMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    val in = ast.lines.mkString("\n")
    previousElement = DocParser.runner(in)
    previousElement
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
          case AST.Opr("=") => tryCreatingTitleFromInfix(ast)
          case _            => infixActionNoTitleFound(ast)
        }
      case _ => infixActionNoTitleFound(ast)
    }
  }

  def tryCreatingTitleFromInfix(ast: AST.App._Infix): AST = {
    println("\n--- FOUND LAMBDA DEFINITION ---\n")
    previousElement match {
      case _: Doc => createTitleFromInfix(ast)
      case _      => infixActionNoTitleFound(ast)
    }
  }

  def createTitleFromInfix(ast: AST.App._Infix): AST = {
    println("\n--- CREATING TITLE ---\n")
    val docFunName = Doc.Elem.Text(ast.larg.show())
    val docHeader  = Doc.Section.Header(docFunName)
    val doc        = Doc(Doc.Synopsis(Doc.Section.Raw(docHeader)))
    pprint.pprintln(doc)
    println(doc.show())
    previousElement = ast
    findCommentsAndTitles(doc)
  }

  def infixActionNoTitleFound(ast: AST): AST = {
    println("\n--- NO TITLE FOUND ---\n")
    ast.map({ elem =>
      println("\n--- --- --- TRYING WITH ELEM (INFIX): ---\n")
      pprint.pprintln(elem, width = 50, height = 10000)
      previousElement = elem
      findCommentsAndTitles(elem)
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
        previousElement = spec.fin(ast.pfx, ast.segs.toList().map(_.el))
        findCommentsAndTitles(previousElement)
    }
  }

  def defaultAction(ast: AST): AST = {
    println("\n--- NO COMMENT FOUND IN THIS ELEMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    ast.map({ elem =>
      println("\n--- --- --- TRY FROM ELEM (DEFAULT): ---\n")
      pprint.pprintln(elem, width = 50, height = 10000)
      previousElement = previousElement match {
        case _: Doc =>
          elem match {
            case _: AST.App._Infix => previousElement
            case _                 => elem
          }
        case _ => elem
      }
      findCommentsAndTitles(elem)
    })
  }
}
