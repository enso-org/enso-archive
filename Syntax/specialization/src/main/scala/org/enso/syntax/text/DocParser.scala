package org.enso.syntax.text

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import org.enso.syntax.text.AST.Documented
import scala.annotation.tailrec
import scala.util.Random
import java.io.File
import java.io.PrintWriter

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

  /**
    * Used to match result of [[run]] function to possibly retrieve Doc
    * @param input - input string to Doc Parser
    * @return - If it was able to retrieve Doc, then retrieved data, else empty
    *           Doc
    */
  def runMatched(input: String): Doc = run(input) match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) => v
    case _                                                        => Doc()
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
  def onHTMLRendering(documented: Documented): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(
      path,
      renderHTML(documented.ast, documented.doc, cssFileName)
    )
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
    val metaEquiv = HTML.httpEquiv := "Content-Type"
    val metaCont  = HTML.content := "text/html"
    val metaChar  = HTML.charset := "UTF-8"
    val meta      = HTML.meta(metaEquiv)(metaCont)(metaChar)
    val cssRel    = HTML.rel := "stylesheet"
    val cssHref   = HTML.href := cssLink
    val css       = HTML.link(cssRel)(cssHref)
    val title = ast match {
      case Some(value) => Seq(HTML.div(HTML.`class` := "Title")(value.show()))
      case None        => Seq()
    }

    val documentation = Seq(
      HTML.div(HTML.`class` := "Documentation")(title, doc.html)
    )
    HTML.html(HTML.head(meta, css), HTML.body(documentation))
  }
}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  /**
    * Doc Parser running methods, as described above
    */
  def runMatched(input: String): Doc =
    new DocParser().runMatched(input)
  def run(input: String): Result[Doc] = new DocParser().run(input)

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
    * creating Docs from parsed comments
    * and also generating HTML files for created Doc's
    *
    * @param ast - parsed data by Parser
    * @return - AST with possible documentation
    */
  def create(ast: AST): AST = {
    val createdDocs = createDocs(ast)
    /* NOTE : Commented out for ease of debugging procedures */
    //generateHTMLForEveryDocumented(createdDocs)
    createdDocs
  }

  /**
    * This function changes single- and multi- line comments into
    * Doc(s), and Infix into Documented(s) ast
    *
    * @param ast - data from Parser
    * @return - modified data containing possibly Documentation(s)
    */
  def createDocs(ast: AST): AST = {
    def accumulator(ast: AST): AST = {
      ast.map {
        case AST.Comment.any(v) => createDocFromComment(v)
        case elem               => accumulator(elem)
      }
    }
    accumulator(ast)
  }

  /**
    * creates Docs from comments found in parsed data
    *
    * @param ast - comment
    * @return - Documentation
    */
  def createDocFromComment(ast: AST.CommentOf[AST]): AST.DocumentedOf[AST] = {
    val in = ast.lines.mkString("\n")
    AST.Documented(DocParser.runMatched(in))
  }

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
        case AST.Documented.any(d) =>
          new DocParser().onHTMLRendering(d)
        case _ =>
      }
      elem
    }
  }
}
