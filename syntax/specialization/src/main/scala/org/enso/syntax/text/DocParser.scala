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
import org.enso.data.List1

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
  var previousElement: AST = AST.Blank

  /** create - function for invoking DocParser in right places
    * creating documentation from parsed comments
    * and also generating HTML files for created Documentation's
    *
    * @param ast - parsed data by Parser
    * @return - AST with possible documentation
    */
  def create(ast: AST.Module): AST = {
    val createdDocs = createDocs(ast)
    val preparedDocs = createdDocs match {
      case mod: AST.Module => reformatDocumentation(mod, ast)
    }
    /* NOTE : Commented out just for ease of debugging procedures */
//    generateHTMLForEveryDocumentation(preparedDocs)
    preparedDocs
  }

  /** createDocs - This function changes single- and multi- line comments into
    * Documented(s), and Infix into Documentation Title
    *
    * @param ast - data from Parser
    * @return - modified data containing possibly Documentation(s)
    */
  def createDocs(ast: AST): AST = {
    ast.map { elem =>
      previousElement = elem match {
        case v: AST.Comment.MultiLine  => multiLineAction(v)
        case v: AST.Comment.SingleLine => singleLineAction(v)
        case v: AST.App._Infix         =>
          /* NOTE - Only create title if infix is right under Doc */
          previousElement match {
            case documented: Documented =>
              infixAction(v, documented) match {
                case Some(documentation) => documentation
                case None                => v
              }
            case _ => v
          }
        // FIXME - THIS IS UUUUGLY, still, next PR
//        case v: AST.Def =>
//          /* NOTE - Only create title if def is right under Doc */
//          previousElement match {
//            case documented: Documented =>
//              v.body match {
//                case Some(b) =>
//                  b match {
//                    case c: AST.Block =>
//                      AST.Module(
//                        AST.Block
//                          ._Line(Some(defAction(v, documented)), c.indent),
//                        AST.Block._Line(Some(createDocs(v)), c.indent)
//                      )
//                    case _ =>
//                      AST.Module(
//                        AST.Block
//                          ._Line(Some(defAction(v, documented)), 0),
//                        AST.Block._Line(Some(createDocs(v)), 0)
//                      )
//                  }
//                case None => defAction(v, documented)
//              }
//            case _ => v
//          }
        case v => createDocs(v)
      }
      previousElement
    }
  }

  /** Single Line Action - creates Doc from comment
    *
    * @param ast - Single line comment
    * @return - Documentation from single line comment
    */
  def singleLineAction(ast: AST.Comment.SingleLine): Documented = {
    val in = ast.text
    DocParser.runMatched(in)
  }

  /** Multi Line Action - creates Doc from comment
    *
    * @param ast - Multi line comment
    * @return - Documentation from multi line comment
    */
  def multiLineAction(ast: AST.Comment.MultiLine): Documented = {
    val in = ast.lines.mkString("\n")
    DocParser.runMatched(in)
  }

  /** Infix Action - Tries to create Doc Title from function name
    *
    * @param ast - Infix
    * @return - Documentation title from infix left argument
    */
  def infixAction(
    ast: AST.App._Infix,
    doc: Documented
  ): Option[Documentation] = {
    ast.larg match {
      case v: AST._App => Some(Documentation(Some(v.show()), doc))
      case _           => None
    }
  }

  /** Def Action - Tries to create Doc Title from def function name
    *
    * @param ast - Def
    * @return - Documentation title from def name
    */
  def defAction(
    ast: AST.Def,
    doc: Documented
  ): Documentation = {
    Documentation(Some(ast.name.show()), doc)
  }

  /** reformatDocumentation
    *
    * @param astWithDoc - ast after running DocParser on it
    * @param astBeginning - primary AST without modifications
    * @return - properly oriented AST with Documentation(Title,Documented) elems
    */
  def reformatDocumentation(
    astWithDoc: AST.Module,
    astBeginning: AST.Module
  ): AST.Module = {
    var astDoc = astWithDoc
    astWithDoc.lines.zipWithIndex.map { elem =>
      elem._1.elem.map {
        case v: Documentation =>
          // NOTE : Documented(before comment) -> Documentation
          val DocToLine = AST.Block._Line(Some(v), 0)
          val updatedWithDoc =
            astDoc.lines.toList.updated(elem._2 - 1, DocToLine)
          // NOTE : Documentation -> Infix (to get back func. def)
          val infix            = astBeginning.lines.toList(elem._2)
          val updatedWithInfix = updatedWithDoc.updated(elem._2, infix)

          astDoc = AST.Module(List1(updatedWithInfix).get)
//        case v: AST.Def => //FIXME - Loop through Def's
//          if (astBeginning.lines.toList.length <= elem._2) {
//            astBeginning.lines.toList(elem._2).elem match {
//              case Some(value) =>
//                value match {
//                  case d: AST.Def =>
//                    d.body match {
//                      case Some(value2) =>
//                        value2 match {
//                          case b: AST.Block =>
//                            reformatBlock(v.body.get.asInstanceOf[AST.Block], b)
//                          case _ =>
//                        }
//                      case None =>
//                    }
//                  case _ =>
//                }
//              case None =>
//            }
//          }
        case _ =>
      }
    }
    astDoc
  }

//  def reformatBlock(
//    astWithDoc: AST.Block,
//    astBeginning: AST.Block
//  ): AST.Block = {
//    var astDoc = astWithDoc
//    astWithDoc.lines.zipWithIndex.map { elem =>
//      elem._1.elem.map {
//        case v: Documentation =>
//          // NOTE : Documented(before comment) -> Documentation
//          val DocToLine = AST.Block._Line(Some(v), 0)
//          val updatedWithDoc =
//            astDoc.lines.updated(elem._2 - 1, DocToLine)
//          // NOTE : Documentation -> Infix (to get back func. def)
//          val infix            = astBeginning.lines(elem._2)
//          val updatedWithInfix = updatedWithDoc.updated(elem._2, infix)
//
//          astDoc = AST._Block(
//            AST.Block.Continuous,
//            astWithDoc.indent,
//            astWithDoc.emptyLines,
//            updatedWithInfix.head.asInstanceOf[AST.Block.Line.NonEmpty],
//            updatedWithInfix.tail
//          )
//        case v: AST.Def => //FIXME - Loop through Def's
//          if (astBeginning.lines.length <= elem._2) {
//            astBeginning.lines(elem._2).elem match {
//              case Some(value) =>
//                value match {
//                  case d: AST.Def =>
//                    d.body match {
//                      case Some(value2) =>
//                        value2 match {
//                          case b: AST.Block =>
//                            reformatBlock(v.body.get.asInstanceOf[AST.Block], b)
//                          case _ =>
//                        }
//                      case None =>
//                    }
//                  case _ =>
//                }
//              case None =>
//            }
//          }
//        case _ =>
//      }
//    }
//    astDoc
//  }

  /** generateHTMLForEveryDocumentation - this method is used for generation of
    * HTML files from parsed and reformatted Documentation(s) and/or Documented(s)
    *
    * @param ast - parsed AST.Module and reformatted using Doc Parser
    */
  def generateHTMLForEveryDocumentation(ast: AST.Module): Unit = {
    ast.map { elem =>
      elem match {
        case v: Documentation =>
          new DocParser().onHTMLRendering(v)
        case v: Documented =>
          new DocParser().onHTMLRendering(Documentation(None, v))
        case _ =>
      }
      elem
    }
  }
}
