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
    * @param ast - possible title from infix / def made by Doc Parser Runner
    * @param doc - Doc created with Doc Parser
    */
  def onHTMLRendering(ast: AST, doc: Doc): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(path, renderHTML(ast, doc, cssFileName))
  }

  /**
    * Function invoked by [[onHTMLRendering]] to render HTML File
    * @param t - title from Doc Parser Runner
    * @param doc - Doc from Doc Parser
    * @param cssLink - string containing CSS file name
    * @return - HTML Code from Doc with optional title from AST
    */
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

  /**
    * Doc Parser running methods, described above
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

/*
  TODO [MM] ( to rewrite )
   1. Parsing code containing comment with function defined in next line -
      nothing more complicated. In result we want documentation just like we get
      now, but with title created from function name. After updating code to
      newest parser ( lets assume wdanilo/cata branch is your reference now on)
      make a PR onto it. It may be dirty here and there, but it should work

  TODO [MM] ( done, in review )
   2. Add to our AST node "documented" which will contain two fields - other AST
      and your Documentation. Your doc shouldn't extend AST ( it doesn't on my
      branch already ) - separate PR

  TODO [MM] ( up next )
   3. Now use case working like that - in code we have `def Maybe` with doc
      above it and to every function in it (refer to parser tests). We want to
      traverse it and make final form of documentation. Take from def name of
      documentation, show documentation of every internal function just like
      apple displays on grey background in Developer.Apple.com Documentation.
      Generally implementation here should be extremely easy as def is block
      which contains list of lines so you loop through it and if you find Doc
      then check if next line is empty or contain function ( and you have it
      already from point 1 )
 */

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser Runner /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
//
///**
//  * This is Doc Parser Runner. Essentially it binds together Enso Parser with
//  * Doc Parser. When Parser finishes its job it invokes runner with its created
//  * AST with resolved macros. Then Doc Parser Runner does it's job - running Doc
//  * Parser on every [[AST.Comment]], Trying to find title for [[AST.Documented]]
//  * from [[AST.App.Infix]] below comments.
//  */
//object DocParserRunner {
//  var previousElement: Option[AST] = None
//
//  /**
//    * function for invoking DocParser in right places
//    * creating Docs from parsed comments
//    * and also generating HTML files for created Doc's
//    *
//    * @param ast - parsed data by Parser
//    * @return - AST with possible documentation
//    */
//  def create(ast: AST): AST = {
//    val createdDocs = createDocs(ast)
//    val preparedDocs = createdDocs.unFix match {
//      case AST.ModuleOf(m) =>
//        ast.unFix match {
//          case AST.ModuleOf(v) => reorganiseDocs(m, v)
//          case _               => createdDocs
//        }
//      case _ => createdDocs
//    }
//    /* NOTE : Commented out for ease of debugging procedures */
//    //generateHTMLForEveryDocumented(preparedDocs)
//    preparedDocs
//  }
//
//  /**
//    * This function changes single- and multi- line comments into
//    * Doc(s), and Infix into Documented(s) title
//    *
//    * @param ast - data from Parser
//    * @return - modified data containing possibly Documentation(s)
//    */
//  def createDocs(ast: AST): AST = {
//    ast.map { elem =>
//      previousElement = Some(elem.unFix match {
//        case AST.CommentOf(v)        => createDocFromComment(AST.Comment(v))
//        case v: AST.App.InfixOf[AST] => infixFoundWhileCreatingDocs(v)
////        case v: AST.DefOf[AST] =>
////          defFoundWhileCreatingDocs(v)
////        // Maybe just call create(v) ?
//        case _ => createDocs(elem)
//      })
//      previousElement.get
//    }
//  }
//
//  /**
//    * Action taken when infix has been found in AST
//    * @param v - infix
//    * @return - either created Documented or the same infix
//    */
//  private def infixFoundWhileCreatingDocs(v: AST.App.InfixOf[AST]): AST = {
//    // NOTE : [Infix handling]
//    previousElement match {
//      case Some(value) =>
//        value.unFix match {
//          case AST.DocumentedOf(ast, doc) =>
//            ast.unFix match {
//              case AST.Ident.ConsOf("") =>
//                createDocumentedWithTitleFromInfix(v, AST.Documented(ast, doc))
//              case _ => v
//            }
//          case _ => v
//        }
//      case _ => v
//    }
//  }
//
//  /* NOTE : [Infix handling]
//   * ~~~~~~~~~~~~~~~~~~~~~~~
//   * This function firstly checks if previous element ( one before infix ) is
//   * Documented created by Doc Parser from comment, if it is, then it checks if
//   * it has already a title, if it doesn't have it creates new Documented with
//   * Doc from previous element, and title from currently handled infix, in other
//   * case it leaves infix as is
//   */
//
//  /**
//    * TODO [MM] - still WIP, point 3
//    * Action taken when def has been found in AST
//    * @param v - Def
//    * @return - either created Documented or the same def
//    */
//  private def defFoundWhileCreatingDocs(v: AST.DefOf[AST]): AST = {
//    previousElement match {
//      case Some(value) =>
//        value.unFix match {
//          case AST.DocumentedOf(ast, doc) =>
//            ast.unFix match {
//              case AST.Ident.ConsOf("") =>
//                createDocumentedWithTitleFromDef(v, AST.Documented(ast, doc))
//              case _ => v
//            }
//          case _ => v
//        }
//      case _ => v
//    }
//  }
//
//  /**
//    * creates Docs from comments found in parsed data
//    *
//    * @param ast - comment
//    * @return - Documentation
//    */
//  def createDocFromComment(
//    ast: AST.CommentOf[AST]
//  ): AST.DocumentedOf[AST] = {
//    val in = ast.lines.mkString("\n")
//    // FIXME [MM] : (AST.Cons("")) HOW DO I CREATE EMPTY AST()?
//    AST.Documented(AST.Cons(""), DocParser.runMatched(in))
//  }
//
//  /**
//    * Tries to create Doc Title from function name
//    *
//    * @param ast - Infix
//    * @return - Documentation title from infix left argument
//    */
//  def createDocumentedWithTitleFromInfix(
//    ast: AST.App.InfixOf[AST],
//    partialDoc: AST.DocumentedOf[AST]
//  ): AST.DocumentedOf[AST] = {
//    AST.Documented(ast.larg, partialDoc.doc)
//  }
//
//  /**
//    * Tries to create Doc Title from def function name
//    *
//    * @param ast - Def
//    * @return - Documentation title from def name
//    */
//  def createDocumentedWithTitleFromDef(
//    ast: AST.DefOf[AST],
//    partialDoc: AST.DocumentedOf[AST]
//  ): AST.DocumentedOf[AST] = {
//    AST.Documented(ast.name, partialDoc.doc)
//  }
//
//  /**
//    * reorganizes documented AST with properly placed [[AST.Documented]]
//    * and [[AST.App.Infix]]
//    *
//    * @param astWithDoc - ast after running DocParser on it
//    * @param astFromParser - primary AST without modifications
//    * @return - properly oriented AST with Documentation(Title,Documented) elems
//    */
//  def reorganiseDocs(
//    astWithDoc: List1[AST.Block.OptLineOf[AST]],
//    astFromParser: List1[AST.Block.OptLineOf[AST]]
//  ): AST.Module = {
//    var astDoc = astWithDoc
//    astWithDoc.zipWithIndex.map { elem =>
//      val currLine  = elem._1
//      val currIndex = elem._2
//      currLine.elem.map { e =>
//        e.unFix match {
//          case AST.DocumentedOf(ast, doc) =>
//            swapInfixWithDocumentedIntoDocs(
//              currIndex,
//              AST.Documented(ast, doc),
//              astWithDoc,
//              astFromParser
//            ) match {
//              case Some(value) => astDoc = value
//              case None        =>
//            }
////          case AST.DefOf(a, b, c) =>
////            println("a : " + a)
////            println("b : " + b)
////            println("c : " + c)
////            astFromParser.toList(currIndex).elem.get.unFix match {
////              case AST.DefOf(d, e, f) =>
////                println()
////                println("d : " + d)
////                println("e : " + e)
////                println("f : " + f)
////                println("========")
////
////                c match {
////                  case Some(cv) =>
////                    f match {
////                      case Some(fv) =>
////                        reorganiseDocs(
////                          List1(cv.asInstanceOf[AST.Block].lines).get,
////                          List1(fv.asInstanceOf[AST.Block].lines).get
////                        )
////                      case None =>
////                    }
////                  case None =>
////                }
////              case _ =>
////            }
//          case _ =>
//        }
//      }
//    }
//    AST.Module(astDoc)
//  }
//
//  /**
//    * ran by [[reorganiseDocs]] to properly place Infixes and Documented's
//    *
//    * @param currIndex - current index from mapping in [[reorganiseDocs]]
//    * @param documented - documented found from [[reorganiseDocs]]
//    * @param astDoc - AST after running [[createDocs]]
//    * @param astFromParser - AST passed into [[create]]
//    * @return - AST with properly created Documented and Infix
//    */
//  def swapInfixWithDocumentedIntoDocs(
//    currIndex: Int,
//    documented: AST.DocumentedOf[AST],
//    astDoc: List1[AST.Block.OptLineOf[AST]],
//    astFromParser: List1[AST.Block.OptLineOf[AST]]
//  ): Option[List1[AST.Block.OptLineOf[AST]]] = {
//    documented.ast.unFix match {
//      case AST.Ident.ConsOf("") => None
//      // NOTE : [Documented with infix handling]
//      case _ =>
//        val DocToLine        = AST.Block.OptLine(documented)
//        val prevIndex: Int   = currIndex - 1
//        val updatedWithDoc   = astDoc.toList.updated(prevIndex, DocToLine)
//        val infix            = astFromParser.toList(currIndex)
//        val updatedWithInfix = updatedWithDoc.updated(currIndex, infix)
//        List1(updatedWithInfix)
//    }
//  }
//  /* NOTE : [Documented with infix handling]
//   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//   * This function is used to properly place elements in AST after creating
//   * Documented(s). When creating documentation with title from infix, runner
//   * firstly creates Documented with Doc from comment, then the next element can
//   * be Documented with title from infix and Doc from previous element, as I've
//   * described above. This leaves us with 2 things to fix - firstly, there are
//   * now 2 documented's in AST, moreover we've lost infix during mapping. So now
//   * this function removes first Documented without title, moves to it's place
//   * documented with title from infix, and to that place moves infix from AST on
//   * beginning ( the one Doc Parser got from Enso Parser )
//   */
//
//  /**
//    * this method is used for generation of
//    * HTML files from parsed and reformatted
//    * Documented(s)
//    *
//    * @param ast - parsed AST.Module and reformatted using Doc Parser
//    */
//  def generateHTMLForEveryDocumented(ast: AST): Unit = {
//    ast.map { elem =>
//      elem.unFix match {
//        case AST.DocumentedOf(ast, doc) =>
//          new DocParser().onHTMLRendering(ast, doc)
//        case _ =>
//      }
//      elem
//    }
//  }
//}
