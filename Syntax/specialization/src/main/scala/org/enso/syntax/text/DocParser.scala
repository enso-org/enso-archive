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
    val createdDocs        = createDocs(module)
    val createdDocsWithAST = createDocsWithAST(createdDocs)
    /* NOTE : Commented out for ease of debugging procedures */
//    generateHTMLForEveryDocumented(createdDocsWithAST)
    createdDocsWithAST
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Doc creation from comments //////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /**
    * This function changes comments into Doc(s) with empty AST
    *
    * @param module - data from Parser
    * @return - modified data containing possibly Documentation(s) without AST
    */
  def createDocs(module: AST): AST = {
    def accumulator(ast: AST): AST = {
      ast.map {
        case AST.Comment.any(elem) => createDocFromComment(elem)
        case elem                  => accumulator(elem)
      }
    }
    accumulator(module)
  }

  /**
    * creates Docs from comments found in parsed data
    *
    * @param comment - comment found in AST
    * @return - Documentation
    */
  def createDocFromComment(comment: AST.Comment): AST.Documented = {
    val in = comment.lines.mkString("\n")
    AST.Documented(DocParser.runMatched(in))
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Assigning AST to previously created Doc's ///////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /**
    * This function gets Doc(s) with originally empty AST and tries to assign
    * to them appropriate AST from Infix's and Def's
    *
    * @param ast - module with possibly Doc's
    * @return - modified data containing possibly Documentation(s) with AST
    */
  def createDocsWithAST(ast: AST): AST = {
    ast match {
      case AST.Module.any(m) =>
        val transformedLines = List1(transformLines(m.lines.toList))
          .getOrElse(List1(AST.Block.OptLine()))
        AST.Module(transformedLines)
      case AST.Def.any(d) =>
        d.body match {
          case Some(defBody) =>
            defBody match {
              case AST.Block.any(block) =>
                val firstLine =
                  Line(Option(block.firstLine.elem), block.firstLine.off)
                val linesToTransform = firstLine :: block.lines
                val transformedLines = transformLines(linesToTransform)
                val lines            = transformedLines.tail
                val body = AST.Block(
                  block.typ,
                  block.indent,
                  block.emptyLines,
                  AST.Block.LineOf[AST](
                    transformedLines.head.elem.get,
                    transformedLines.head.off
                  ),
                  lines
                )
                AST.Def(d.name, d.args, Some(body))
              case _ => d
            }
          case None => d
        }
      case o => o
    }
  }

  def transformLines(lines: List[AST.Block.OptLine]): List[AST.Block.OptLine] =
    lines match {
      case Line(Some(AST.Documented.any(doc)), off1) :: Line(
            Some(AST.App.Infix.any(ast)),
            _
          ) :: rest =>
        Line(Some(AST.Documented(doc.doc, ast)), off1) :: transformLines(rest)
      case Line(Some(AST.Documented.any(doc)), off1) :: Line(
            Some(AST.Def.any(ast)),
            _
          ) :: rest =>
        Line(Some(AST.Documented(doc.doc, createDocsWithAST(ast))), off1) :: transformLines(
          rest
        )
      case x :: rest =>
        x :: transformLines(rest)
      case other =>
        other
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
