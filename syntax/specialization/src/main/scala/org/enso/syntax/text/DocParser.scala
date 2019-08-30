package org.enso.syntax.text

import java.io.File
import java.io.PrintWriter

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Documentation
import org.enso.syntax.text.ast.Documented
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import scala.util.Random

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

class DocParser {
  import DocParser._
  private val engine = newEngine()

  def runMatched(input: String): Documented = run(input) match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) => v
    case _                                                        => Documented()
  }
  def run(input: String): Result[Documented] = engine.run(new Reader(input))

  def onHTMLRendering(doc: Documentation): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(path, renderHTML(doc, cssFileName))
  }

  def renderHTML(
    doc: Documentation,
    cssLink: String = "style.css"
  ): TypedTag[String] = {
    val metaEquiv = HTML.httpEquiv := "Content-Type"
    val metaCont  = HTML.content := "text/html"
    val metaChar  = HTML.charset := "UTF-8"
    val meta      = HTML.meta(metaEquiv)(metaCont)(metaChar)
    val cssRel    = HTML.rel := "stylesheet"
    val cssHref   = HTML.href := cssLink
    val css       = HTML.link(cssRel)(cssHref)
    HTML.html(HTML.head(meta, css), HTML.body(doc.html))
  }
}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  def runMatched(input: String): Documented =
    new DocParser().runMatched(input)
  def run(input: String): Result[Documented] = new DocParser().run(input)

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

  /** create - function for invoking DocParser in right places
    * and creating documentation from parsed comments
    *
    * @param ast - parsed data
    * @return - AST with possible documentation
    */
  def create(ast: AST.Module): AST = {
    ast.map { elem =>
      println("ELEM OF AST : " + elem)
      elem match {
        case v: AST.Comment.MultiLine  => multiLineAction(v)
        case v: AST.Comment.SingleLine => singleLineAction(v)
        case v: AST.App._Infix =>
          infixAction(v) match {
            case Some(d) => d
            case None    => ast
          }
        case v => v
      }
    }
  }

  /** Single Line Action - creates Doc from comment
    * (should be Doc(Synopsis(Raw())) )
    *
    * @param ast - Single line comment
    * @return - Documentation from single line comment
    */
  def singleLineAction(ast: AST.Comment.SingleLine): Documented = {
    println("\n--- FOUND SINGLE LINE COMMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    val in = ast.text
    DocParser.runMatched(in)
  }

  /** Multi Line Action - creates Doc from comment
    *
    * @param ast - Multi line comment
    * @return - Documentation from multi line comment
    */
  def multiLineAction(ast: AST.Comment.MultiLine): Documented = {
    println("\n--- FOUND MULTI LINE COMMENT ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    val in = ast.lines.mkString("\n")
    DocParser.runMatched(in)
  }

  /** Infix Action - Tries to create Doc Title from function name
    *
    * @param ast - Infix
    * @return - Documentation title from infix left argument
    */
  def infixAction(ast: AST.App._Infix): Option[Documentation] = {
    println("\n--- FOUND INFIX ---\n")
    pprint.pprintln(ast, width = 50, height = 10000)
    ast.larg match {
      case v: AST._App => Some(Documentation(Some(ast.show()), Documented()))
      case _           => None
    }
  }
}

//// All old code - recursion approach
//  def loopToOrientDocs(ast: AST): AST = {
//    ast match {
//      case v: Doc =>
//        if (prevDoc == Doc()) {
//          println("DOC FOUND! : " + v + "\n")
//          prevDoc = v
//          AST.Module(AST.Block._Line(Some(v), 0))
//        } else {
//          println("DOC CREATED! : " + v + "\n")
//          val head = AST.Block._Line(Some(v), 0)
//          val body = AST.Block._Line(Some(prevDoc), 0)
//          prevDoc = Doc()
//
//          AST.Module(head, body)
//        }
//      case v =>
//        println("NO DOC : " + v + "\n")
//        v.map({ elem =>
//          println("LOOPING : " + elem + "\n")
//          loopToOrientDocs(elem)
//        })
//    }
//  }
//  def findCommentsAndTitles(ast: AST): AST = {
//    println("PREVIOUS ELEMENT: " + previousElement + "--- --- --- --- --- ---")
//    ast match {
//      case v: AST.Macro.Match        => macroMatchAction(v)
//      case v: AST.Comment.MultiLine  => MultilineAction(v)
//      case v: AST.Comment.SingleLine => SinglelineAction(v)
//      case v: AST.App._Infix         => infixAction(v)
//      case v                         => defaultAction(v)
//    }
//  }
//  def infixAction(ast: AST.App._Infix): AST = {
//    println("\n--- FOUND INFIX ---\n")
//    pprint.pprintln(ast, width = 50, height = 10000)
//    println("--- L ARG: " + ast.larg)
//    println("--- L OFF: " + ast.loff)
//    println("--- OPR  : " + ast.opr)
//    println("--- R ARG: " + ast.rarg)
//    println("--- R OFF: " + ast.roff)
//    ast.larg match {
//      case _: AST._App =>
//        ast.opr match {
//          case AST.Opr("=") => tryCreatingTitleFromInfix(ast)
//          case _            => infixActionNoTitleFound(ast)
//        }
//      case _ => infixActionNoTitleFound(ast)
//    }
//  }
//
//  def tryCreatingTitleFromInfix(ast: AST.App._Infix): AST = {
//    println("\n--- FOUND LAMBDA DEFINITION ---\n")
//    previousElement match {
//      case _: Doc => createTitleFromInfix(ast)
//      case _      => infixActionNoTitleFound(ast)
//    }
//  }
//
//  def createTitleFromInfix(ast: AST.App._Infix): AST = {
//    println("\n--- CREATING TITLE ---\n")
//    val docFunName = Doc.Elem.Text(ast.larg.show())
//    val docHeader  = Doc.Section.Header(docFunName)
//    val doc        = Doc(Doc.Synopsis(Doc.Section.Raw(docHeader)))
//    pprint.pprintln(doc)
//    println(doc.show())
//    previousElement = ast
//    findCommentsAndTitles(doc)
//  }
//
//  def infixActionNoTitleFound(ast: AST): AST = {
//    println("\n--- NO TITLE FOUND ---\n")
//    ast.map({ elem =>
//      println("\n--- --- --- TRYING WITH ELEM (INFIX): ---\n")
//      pprint.pprintln(elem, width = 50, height = 10000)
//      previousElement = elem
//      findCommentsAndTitles(elem)
//    })
//  }
//
//  def macroMatchAction(ast: AST.Macro.Match): AST = {
//    Builtin.registry.get(ast.path()) match {
//      case None => throw new Error("Macro definition not found")
//      case Some(spec) =>
//        println("\n--- --- --- MATCH FOUND: ---\n")
//        pprint.pprintln(spec, width = 50, height = 10000)
//        println("\n--- --- --- MATCH FINALIZERS: ---\n")
//        pprint.pprintln(
//          spec.fin(ast.pfx, ast.segs.toList().map(_.el)),
//          width  = 50,
//          height = 10000
//        )
//        previousElement = spec.fin(ast.pfx, ast.segs.toList().map(_.el))
//        findCommentsAndTitles(previousElement)
//    }
//  }
//
//  def defaultAction(ast: AST): AST = {
//    println("\n--- NO COMMENT FOUND IN THIS ELEMENT ---\n")
//    pprint.pprintln(ast, width = 50, height = 10000)
//    ast.map({ elem =>
//      println("\n--- --- --- TRY FROM ELEM (DEFAULT): ---\n")
//      pprint.pprintln(elem, width = 50, height = 10000)
//      previousElement = previousElement match {
//        case _: Doc =>
//          elem match {
//            case _: AST.App._Infix => previousElement
//            case _                 => elem
//          }
//        case _ => elem
//      }
//      findCommentsAndTitles(elem)
//    })
//  }
