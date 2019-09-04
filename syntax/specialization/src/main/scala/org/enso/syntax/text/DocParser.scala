package org.enso.syntax.text

import java.io.File
import java.io.PrintWriter

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag
import scalatags.Text.{all => HTML}
import HTML._
import org.enso.data.List1

import scala.util.Random

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

class DocParser {
  import DocParser._
  private val engine = newEngine()

  def runMatched(input: String): Doc = run(input) match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) => v
    case _                                                        => Doc()
  }
  def run(input: String): Result[Doc] = engine.run(new Reader(input))

  def onHTMLRendering(documented: AST.Documented): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(
      path,
      renderHTML(documented.ast, documented.doc, cssFileName)
    )
  }

  def renderHTML(
    t: AST,
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
    val title     = Seq(HTML.div(HTML.`class` := "Title")(t.show()))
    HTML.html(HTML.head(meta, css), HTML.body(title, doc.html))
  }
}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  def runMatched(input: String): Doc =
    new DocParser().runMatched(input)
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

//TODO ( done )
// 1. Parsing code containing comment with function defined in next line -
//    nothing more complicated. In result we want documentation just like we get
//    now, but with title created from function name. After updating code to
//    newest parser ( lets assume wdanilo/cata branch is your reference now on)
//    make a PR onto it. It may be dirty here and there, but it should work
//
//TODO ( done )
// 2. Add to our AST node "documented" which will contain two fields - other AST
//    and your Documentation. Your doc shouldn't extend AST ( it doesn't on my
//    branch already ) - separate PR
//
//TODO ( up next )
// 3. Now use case working like that - in code we have `def Maybe` with doc
//    above it and to every function in it (refer to parser tests). We want to
//    traverse it and make final form of documentation. Take from def name of
//    documentation, show documentation of every internal function just like
//    apple displays on grey background in Developer.Apple.com Documentation.
//    Generally implementation here should be extremely easy as def is block
//    which contains list of lines so you loop through it and if you find Doc
//    then check if next line is empty or contain function ( and you have it
//    already from point 1 )

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser Runner /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

object DocParserRunner {
  var previousElement: Option[AST] = None

  /** create
    * function for invoking DocParser in right places
    * creating Docs from parsed comments
    * and also generating HTML files for created Doc's
    *
    * @param ast - parsed data by Parser
    * @return - AST with possible documentation
    */
  def create(ast: AST): AST = {
    val createdDocs = createDocs(ast)
    val preparedDocs = createdDocs match {
      case m: AST.Module =>
        ast match {
          case v: AST.Module => reorganiseDocs(m, v)
          case _             => createdDocs
        }
      case _ => createdDocs
    }
    /* NOTE : Comment out for ease of debugging procedures */
//    generateHTMLForEveryDocumentation(preparedDocs)
    preparedDocs
  }

  /** createDocs
    * This function changes single- and multi- line comments into
    * Doc(s), and Infix into Documented(s) title
    *
    * @param ast - data from Parser
    * @return - modified data containing possibly Documentation(s)
    */
  def createDocs(ast: AST): AST = {
    ast.map { elem =>
      println("ELEM IS : " + elem.toString)
      println("PREVIOUS ELEM IS : " + previousElement.toString)
      previousElement = Some(elem.unFix match {
        case v: AST.CommentOf[AST] =>
          println("==============================================")
          println("==    CREATING DOCUMENTATION FROM COMMENT   ==")
          println("==============================================")
          createDocFromComment(v)
        case v: AST.App.InfixOf[AST] =>
          println("==============================================")
          println("==         CREATING TITLE FROM INFIX        ==")
          println("==============================================")
          infixFoundWhileCreatingDocs(v)
        case v: AST.DefOf[AST] =>
          println("==============================================")
          println("==          CREATING TITLE FROM DEF         ==")
          println("==============================================")
          defFoundWhileCreatingDocs(v)
        case _ =>
          println("==============================================")
          println("==                  LOOPING                 ==")
          println("==============================================")
          createDocs(elem)
      })
      previousElement.get
    }
  }

  private def infixFoundWhileCreatingDocs(v: AST.App.InfixOf[AST]): AST = {
    /* NOTE : Only create title if infix is right under Doc */
    previousElement match {
      case Some(value) =>
        value.unFix match {
          case d: AST.DocumentedOf[AST] =>
            d.ast.unFix match {
              /* NOTE : If there is no title yet in `Documented` */
              case AST.Ident.ConsOf("") =>
                createDocumentedWithTitleFromInfix(v, d)
              case _ => v
            }
          case _ => v
        }
      case _ => v
    }
  }

  private def defFoundWhileCreatingDocs(v: AST.DefOf[AST]): AST = {
    /* NOTE : Only create title if infix is right under Doc */
    previousElement match {
      case Some(value) =>
        value.unFix match {
          case d: AST.DocumentedOf[AST] =>
            d.ast.unFix match {
              /* NOTE : If there is no title yet in `Documented` */
              case AST.Ident.ConsOf("") =>
                createDocumentedWithTitleFromDef(v, d)
              case _ => v
            }
          case _ => v
        }
      case _ => v
    }
  }

  /** createDocFromComment
    * creates Doc from comment found in parsed data
    *
    * @param ast - comment
    * @return - Documentation
    */
  def createDocFromComment(
    ast: AST.CommentOf[AST]
  ): AST.DocumentedOf[AST] = {
    val in = ast.lines.mkString("\n")
    // FIXME (AST.Cons("")) HOW DO I CREATE EMPTY AST()?
    AST.DocumentedOf[AST](AST.Cons(""), DocParser.runMatched(in))
  }

  /** createDocumentedWithTitleFromInfix
    * Tries to create Doc Title from function name
    *
    * @param ast - Infix
    * @return - Documentation title from infix left argument
    */
  def createDocumentedWithTitleFromInfix(
    ast: AST.App.InfixOf[AST],
    partialDoc: AST.DocumentedOf[AST]
  ): AST.DocumentedOf[AST] = {
    AST.DocumentedOf[AST](ast.larg, partialDoc.doc)
  }

  /** createDocumentedWithTitleFromDef
    * Tries to create Doc Title from def function name
    *
    * @param ast - Def
    * @return - Documentation title from def name
    */
  def createDocumentedWithTitleFromDef(
    ast: AST.DefOf[AST],
    partialDoc: AST.DocumentedOf[AST]
  ): AST.DocumentedOf[AST] = {
    AST.DocumentedOf[AST](ast.name, partialDoc.doc)
  }

  /** reorganiseDocs
    *
    * @param astWithDoc - ast after running DocParser on it
    * @param astFromParser - primary AST without modifications
    * @return - properly oriented AST with Documentation(Title,Documented) elems
    */
  def reorganiseDocs(
    astWithDoc: AST.Module,
    astFromParser: AST.Module
  ): AST.Module = {
    var astDoc = astWithDoc
    astWithDoc.lines.zipWithIndex.map { elem =>
      val currElem  = elem._1
      val currIndex = elem._2
      currElem.elem.map { e =>
        e.unFix match {
          case v: AST.DocumentedOf[AST] =>
            v.ast.unFix match {
              case AST.Ident.ConsOf("") => // Documented without title
              case _                    =>
                // NOTE : Documented(before comment) -> Documentation
                val DocToLine      = AST.Block.OptLine(v)
                val prevIndex: Int = currIndex - 1
                val updatedWithDoc =
                  astDoc.unFix.lines.toList.updated(prevIndex, DocToLine)
                // NOTE : Documentation -> Infix (to get back func. def)
                val infix            = astFromParser.unFix.lines.toList(currIndex)
                val updatedWithInfix = updatedWithDoc.updated(currIndex, infix)

                astDoc = AST.ModuleOf[AST](List1(updatedWithInfix).get)
            }
          case _ =>
        }
      }
    }
    astDoc
  }

  /** generateHTMLForEveryDocumentation
    * this method is used for generation of
    * HTML files from parsed and reformatted
    * Documentation(s) and/or Documented(s)
    *
    * @param ast - parsed AST.Module and reformatted using Doc Parser
    */
  def generateHTMLForEveryDocumented(ast: AST.Module): Unit = {
    ast.map { elem =>
      elem.unFix match {
        case v: AST.DocumentedOf[AST] =>
          new DocParser().onHTMLRendering(v)
        case _ =>
      }
      elem
    }
  }
}
