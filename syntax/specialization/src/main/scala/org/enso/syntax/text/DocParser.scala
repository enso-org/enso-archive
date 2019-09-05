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

  def onHTMLRendering(ast: AST, doc: Doc): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(path, renderHTML(ast, doc, cssFileName))
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
    val documentation = Seq(
      HTML.div(HTML.`class` := "Documentation")(title, doc.html)
    )
    HTML.html(HTML.head(meta, css), HTML.body(documentation))
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
    val preparedDocs = createdDocs.unFix match {
      case AST.ModuleOf(m) =>
        ast.unFix match {
          case AST.ModuleOf(v) => reorganiseDocs(m, v)
          case _               => createdDocs
        }
      case _ => createdDocs
    }
    /* NOTE : Comment out for ease of debugging procedures */
    //generateHTMLForEveryDocumented(preparedDocs)
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
      previousElement = Some(elem.unFix match {
        case AST.CommentOf(v)        => createDocFromComment(AST.Comment(v))
        case v: AST.App.InfixOf[AST] => infixFoundWhileCreatingDocs(v)
        case v: AST.DefOf[AST]       => defFoundWhileCreatingDocs(v)
        case _                       => createDocs(elem)
      })
      previousElement.get
    }
  }

  private def infixFoundWhileCreatingDocs(v: AST.App.InfixOf[AST]): AST = {
    /* NOTE : Only create title if infix is right under Doc */
    previousElement match {
      case Some(value) =>
        value.unFix match {
          case AST.DocumentedOf(ast, doc) =>
            ast.unFix match {
              /* NOTE : If there is no title yet in `Documented` */
              case AST.Ident.ConsOf("") =>
                createDocumentedWithTitleFromInfix(v, AST.Documented(ast, doc))
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
          case AST.DocumentedOf(ast, doc) =>
            ast.unFix match {
              /* NOTE : If there is no title yet in `Documented` */
              case AST.Ident.ConsOf("") =>
                createDocumentedWithTitleFromDef(v, AST.Documented(ast, doc))
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
    AST.Documented(AST.Cons(""), DocParser.runMatched(in))
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
    AST.Documented(ast.larg, partialDoc.doc)
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
    AST.Documented(ast.name, partialDoc.doc)
  }

  /** reorganiseDocs
    *
    * @param astWithDoc - ast after running DocParser on it
    * @param astFromParser - primary AST without modifications
    * @return - properly oriented AST with Documentation(Title,Documented) elems
    */
  def reorganiseDocs(
    astWithDoc: List1[AST.Block.OptLineOf[AST]],
    astFromParser: List1[AST.Block.OptLineOf[AST]]
  ): AST.Module = {
    var astDoc = astWithDoc
    astWithDoc.zipWithIndex.map { elem =>
      val currLine  = elem._1
      val currIndex = elem._2
      currLine.elem.map { e =>
        e.unFix match {
          case AST.DocumentedOf(ast, doc) =>
            swapInfixWithDocumentedIntoDocs(
              currIndex,
              AST.Documented(ast, doc),
              astWithDoc,
              astFromParser
            ) match {
              case Some(value) => astDoc = value
              case None        =>
            }
          case _ =>
        }
      }
    }
    AST.Module(astDoc)
  }

  /** swapInfixWithDocumentedIntoDocs
    *
    * @param currIndex - current index from mapping in [[reorganiseDocs]]
    * @param documented - documented found from [[reorganiseDocs]]
    * @param astDoc - AST after running [[createDocs]]
    * @param astFromParser - AST passed into [[create]]
    * @return - AST with properly created Documented and Infix
    */
  def swapInfixWithDocumentedIntoDocs(
    currIndex: Int,
    documented: AST.DocumentedOf[AST],
    astDoc: List1[AST.Block.OptLineOf[AST]],
    astFromParser: List1[AST.Block.OptLineOf[AST]]
  ): Option[List1[AST.Block.OptLineOf[AST]]] = {
    documented.ast.unFix match {
      case AST.Ident.ConsOf("") => None
      // NOTE : Documented without any AST -> No infix after -> nothing to do
      case _ =>
        // NOTE : Documented( before title ) -> Documented ( w title )
        val DocToLine      = AST.Block.OptLine(documented)
        val prevIndex: Int = currIndex - 1
        val updatedWithDoc = astDoc.toList.updated(prevIndex, DocToLine)
        // NOTE : 2nd Documented ( with title ) -> Infix (to get back func. def)
        val infix            = astFromParser.toList(currIndex)
        val updatedWithInfix = updatedWithDoc.updated(currIndex, infix)
        List1(updatedWithInfix)
    }
  }

  /** generateHTMLForEveryDocumentation
    * this method is used for generation of
    * HTML files from parsed and reformatted
    * Documentation(s) and/or Documented(s)
    *
    * @param ast - parsed AST.Module and reformatted using Doc Parser
    */
  def generateHTMLForEveryDocumented(ast: AST): Unit = {
    ast.map { elem =>
      elem.unFix match {
        case AST.DocumentedOf(ast, doc) =>
          new DocParser().onHTMLRendering(ast, doc)
        case _ =>
      }
      elem
    }
  }
}
