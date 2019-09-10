package org.enso.syntax.text

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._

import scala.util.Random
import java.io.File
import java.io.PrintWriter

import flexer.Parser.{Result => res}
import org.enso.data.List1
import org.enso.syntax.text.AST.Block.{LineOf => Line}

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

/** This is the main Parser class. It is being used to create documentation from
  * commented elements created by Parser. It has been built on the same
  * foundation as Parser, so in order to not duplicate info, please refer to
  * Parser documentation.
  */
class DocParser {
  import DocParser._
  private val engine = newEngine()
  private val errMsg = "Internal Documentation Parser Error"

  /**
    * Used to match result of [[run]] function to possibly retrieve Doc
    * @param input - input string to Doc Parser
    * @return - If it was able to retrieve Doc, then retrieved data, else empty
    *           Doc
    */
  def runMatched(input: String): Doc = run(input) match {
    case res(_, res.Success(v)) => v
    case _                      => throw new Error(errMsg)
  }

  /**
    * Used to initialize Doc Parser with input string to get parsed Doc
    * @param input - input string to Doc Parser
    * @return - unmatched result possibly containing Doc
    */
  def run(input: String): Result[Doc] = engine.run(new Reader(input))

  /**
    * Used to create HTML files from Doc with or without title after
    * Doc Parser Runner finished it's job
    * @param documented - documented made by Doc Parser Runner from AST and Doc
    */
  def onHTMLRendering(documented: AST.Documented): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    val htmlCode    = renderHTML(documented.ast, documented.doc, cssFileName)
    saveHTMLCodeToLocalFile(path, htmlCode)
  }

  /**
    * Function invoked by [[onHTMLRendering]] to render HTML File
    * @param ast - ast from Doc Parser Runner
    * @param doc - Doc from Doc Parser
    * @param cssLink - string containing CSS file name
    * @return - HTML Code from Doc with optional title from AST
    */
  def renderHTML(
    ast: Option[AST],
    doc: Doc,
    cssLink: String = "style.css"
  ): TypedTag[String] = {
    val astHtml = ast match {
      case Some(value) => Seq(HTML.div(HTML.`class` := "Title")(value.show()))
      case None        => Seq()
    }
    val title = ast match {
      case Some(value) => value.show().split("")(0) //first part of string
      case None        => "Enso Documentation"
    }
    val documentation = Seq(
      HTML.div(HTML.`class` := "Documentation")(astHtml, doc.html)
    )
    HTML.html(createHTMLHead(title, cssLink), HTML.body(documentation))
  }

  /**
    * Function invoked by [[renderHTML]] to create HTML.Head part of file
    * @param title - HTML page title
    * @param cssLink - string containing CSS file name
    * @return - HTML Head Code
    */
  def createHTMLHead(title: String, cssLink: String): TypedTag[String] = {
    val metaEquiv = HTML.httpEquiv := "Content-Type"
    val metaCont  = HTML.content := "text/html"
    val metaChar  = HTML.charset := "UTF-8"
    val meta      = HTML.meta(metaEquiv)(metaCont)(metaChar)
    val cssRel    = HTML.rel := "stylesheet"
    val cssHref   = HTML.href := cssLink
    val css       = HTML.link(cssRel)(cssHref)
    val fileTitle = scalatags.Text.tags2.title(title)
    HTML.head(meta, css)(fileTitle)
  }
}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  /**
    * Doc Parser running methods, as described above
    */
  def runMatched(input: String): Doc         = new DocParser().runMatched(input)
  def run(input: String):        Result[Doc] = new DocParser().run(input)

  /**
    * Saves HTML code to file
    * @param path - path to file
    * @param code - HTML code generated with Doc Parser
    */
  def saveHTMLCodeToLocalFile(path: String, code: TypedTag[String]): Unit = {
    val writer = new PrintWriter(
      new File(path + Random.alphanumeric.take(8).mkString("") + ".html")
    )
    writer.write(code.toString)
    writer.close()
  }
}

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser Runner /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

/**
  * This is Doc Parser Runner. Essentially it binds together Enso Parser with
  * Doc Parser. When Parser finishes its job it invokes runner with its created
  * AST with resolved macros. Then Doc Parser Runner does it's job - running Doc
  * Parser on every [[AST.Comment]], combined with connecting Doc with AST in
  * Documentation node, getting AST from Def's and Infix'es
  */
object DocParserRunner {

  /**
    * function for invoking DocParser in right places
    * Firstly it creates Documentation on every Comment
    * Then it traverses through ast to add appropriate AST into
    * previously created Documented's
    * After all that it generates HTML files for created Documented's
    * and outputs modified AST
    *
    * @param module - parsed data by Parser
    * @return - AST with possible documentation
    */
  def create(module: AST.Module): AST = {
    val createdDocsWithAST = createDocs(module)
    /* NOTE : Commented out for ease of debugging procedures */
//    generateHTMLForEveryDocumented(createdDocsWithAST)
    createdDocsWithAST
  }

  /**
    * This function gets [[AST.Module]] or [[AST.Block]]
    * and then invokes on their lines [[transformLines]] function
    * to create Docs from Comments with or without AST from Infix's and
    * Def's
    *
    * @param ast - module with possibility to create Docs from comments
    * @return - modified data containing possibly Documentation(s) with AST
    */
  def createDocs(ast: AST): AST = {
    ast match {
      case AST.Module.any(m) =>
        val transformedLines = List1(transformLines(m.lines.toList))
          .getOrElse(List1(AST.Block.OptLine()))
        AST.Module(transformedLines)
      case AST.Def.any(d) =>
        d.body match {
          case Some(defBody) =>
            defBody match {
              case AST.Block.any(b) =>
                val firstLine        = Line(Option(b.firstLine.elem), b.firstLine.off)
                val linesToTransform = firstLine :: b.lines
                val tLines           = transformLines(linesToTransform)
                val head =
                  AST.Block.LineOf[AST](tLines.head.elem.get, tLines.head.off)
                val lines = tLines.tail
                val body  = AST.Block(b.typ, b.indent, b.emptyLines, head, lines)
                AST.Def(d.name, d.args, Some(body))
              case _ => d
            }
          case None => d
        }
      case o => o
    }
  }

  /**
    * this is a helper function for creating docs with AST.
    * Essentially it traverses through lines and tries to find a pattern on them
    *
    * @param lines - AST lines
    * @return - lines with possibly Doc with added AST
    */
  //FIXME - Add offset to AST line
  def transformLines(lines: List[AST.Block.OptLine]): List[AST.Block.OptLine] =
    lines match {
      case Line(Some(AST.Comment.any(com)), comOff) :: Line(
            Some(AST.App.Infix.any(ast)),
            astOff
          ) :: rest =>
        val doc            = createDocFromComment(com)
        val documentedLine = Line(Some(AST.Documented(doc, ast)), comOff)
        documentedLine :: transformLines(rest)
      case Line(Some(AST.Comment.any(com)), comOff) :: Line(
            Some(AST.Def.any(ast)),
            astOff
          ) :: rest =>
        val doc = createDocFromComment(com)
        val documentedLine =
          Line(Some(AST.Documented(doc, createDocs(ast))), comOff)
        documentedLine :: transformLines(rest)
      case Line(Some(AST.Comment.any(com)), comOff) :: rest =>
        val doc            = createDocFromComment(com)
        val documentedLine = Line(Some(AST.Documented(doc)), comOff)
        documentedLine :: transformLines(rest)
      case x :: rest =>
        x :: transformLines(rest)
      case other =>
        other
    }

  /**
    * creates Docs from comments found in parsed data
    *
    * @param comment - comment found in AST
    * @return - Documentation
    */
  def createDocFromComment(comment: AST.Comment): Doc = {
    val in = comment.lines.mkString("\n")
    DocParser.runMatched(in)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Generating HTML for created Doc's ///////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /**
    * this method is used for generation of
    * HTML files from parsed and reformatted
    * Documented(s)
    *
    * @param ast - parsed AST.Module and reformatted using Doc Parser
    */
  def generateHTMLForEveryDocumented(ast: AST): Unit = {
    ast.map { elem =>
      elem match {
        case AST.Documented.any(d) => new DocParser().onHTMLRendering(d)
        case _                     => generateHTMLForEveryDocumented(elem)
      }
      elem
    }
  }
}
