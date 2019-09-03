package org.enso.syntax.text

import java.io.File
import java.io.PrintWriter

import org.enso.flexer
import org.enso.flexer.Reader
import org.enso.syntax.text.ast.Documentation
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

  def runMatched(input: String): Documentation = run(input) match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) => v
    case _                                                        => Documentation()
  }
  def run(input: String): Result[Documentation] = engine.run(new Reader(input))

  def onHTMLRendering(documented: AST.Comment.Documented): Unit = {
    val path =
      "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
    val cssFileName = "style.css"
    saveHTMLCodeToLocalFile(
      path,
      renderHTML(documented.title, documented.doc, cssFileName)
    )
  }

  def renderHTML(
    t: Option[AST],
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
    val title = Seq(
      HTML.div(HTML.`class` := "Title")(t.map(_.show()))
    )
    HTML.html(HTML.head(meta, css), HTML.body(title, doc.html))
  }
}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  def runMatched(input: String): Documentation =
    new DocParser().runMatched(input)
  def run(input: String): Result[Documentation] = new DocParser().run(input)

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

// BROKEN AFTER MERGE OF AST_CATA BRANCH - PARSER WONT PRODUCE COMMENTS!
//TODO ( almost done )
// 1. Parsowanie kodu zawierajacego komentarz i funkcje w kolejnej linijce - nic
// bardziej skomplikowanego - jeden komentarz i jedna funkcja - w rezultacie
// masz dostac dokumentacje taka jak masz teraz tylko z jej tytulem bedacym
// tytulem tej fumkcji. Po zrobieniu i zaktualizowaniu kodu do najnowszego
// parsera (niech branch wdanilo/cata bedzie dla Ciebie referencyjny teraz),
// zrob do niego PRa i mi podeslij. On jest troche brudny tu i tam,
// ale powinno zyc.
//
//TODO ( almost done )
// 2. Dodaj do naszego AST node "documented" ktory bedize mial dwa fieldy -
// inne AST oraz Twojego Doca. Twoj Doc nie ma byc typu AST
// (na moim branhcu juz nie jest). Osobny PR.
//
//TODO ( up next )
// 3. Teraz use case ktory dziala tak - w kaodzie mamy tlyko def Maybe
// (zobacz w testach) i nad tym dokumentacja + dokumentacja do kazdej funckji
// w srodku - chcemy to straversowac i zrobic ostatecnza forme dokumentacji.
// z Defa pobierasz nazwe dokumentacji a dokumentacje kazdej funkcji wewnetrznej
// wyswietlasz tak jak na tym szarym obszarze w apple docs. Rob to po
// zmergowaniu brancha, bedzie ci duzo latwiej. Ogolnie implementacja tu powinna
// byc super prosta - definicja jest blokiem i ma liste linijek, wiec
// przechdozicsz przez ta liste i jak znajdziesz dokumentacje, sprawdzasz czy
// kolejne linijki to puste lub zawieraja funckje (a to juz masz z punktu 1)

////////////////////////////////////////////////////////////////////////////////
//// Doc Parser Runner /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

object DocParserRunner {
  var previousElement: Option[AST] = None

  /** create - function for invoking DocParser in right places
    * creating documentation from parsed comments
    * and also generating HTML files for created Documentation's
    *
    * @param ast - parsed data by Parser
    * @return - AST with possible documentation
    */
  def create(ast: AST): AST = {
    val createdDocs = createDocs(ast)
//    val preparedDocs = createdDocs match {
//      case mod: AST.Module => reformatDocumentation(mod, ast)
//    }
//    /* NOTE : Comment out for ease of debugging procedures */
//    generateHTMLForEveryDocumentation(preparedDocs)
//    preparedDocs
    createdDocs
  }

  /** createDocs - This function changes single- and multi- line comments into
    * Documented(s), and Infix into Documentation Title
    *
    * @param ast - data from Parser
    * @return - modified data containing possibly Documentation(s)
    */
  def createDocs(ast: AST): AST = {
    ast.map { elem =>
      previousElement = Some(elem match {
        case v: AST.Comment.MultiLineOf[AST]  => multiLineAction(v)
        case v: AST.Comment.SingleLineOf[AST] => singleLineAction(v)
        case v: AST.App.Infix                 =>
          /* NOTE - Only create title if infix is right under Doc */
          previousElement match {
            case Some(d: AST.Comment.DocumentedOf[AST]) =>
              infixAction(v, d) match {
                case Some(doc) => doc
                case None      => v
              }
            case _ => v.asInstanceOf[AST]
          }
        case v => createDocs(v)
      })
      previousElement.get
    }
  }

  /** Single Line Action - creates Doc from comment
    *
    * @param ast - Single line comment
    * @return - Documentation from single line comment
    */
  def singleLineAction(
    ast: AST.Comment.SingleLine
  ): AST.Comment.Documented = {
    val in = ast.text
    AST.Comment.DocumentedOf[AST](None, DocParser.runMatched(in))
  }

  /** Multi Line Action - creates Doc from comment
    *
    * @param ast - Multi line comment
    * @return - Documentation from multi line comment
    */
  def multiLineAction(ast: AST.Comment.MultiLine): AST.Comment.Documented = {
    val in = ast.lines.mkString("\n")
    AST.Comment.DocumentedOf[AST](None, DocParser.runMatched(in))
  }

  /** Infix Action - Tries to create Doc Title from function name
    *
    * @param ast - Infix
    * @return - Documentation title from infix left argument
    */
  def infixAction(
    ast: AST.App.Infix,
    partialDoc: AST.Comment.Documented
  ): Option[AST.Comment.Documented] = {
    ast.larg match {
      case v: AST.App =>
        Some(AST.Comment.DocumentedOf[AST](Some(v), partialDoc.doc))
      case _ => None
    }
  }

  /** Def Action - Tries to create Doc Title from def function name
    *
    * @param ast - Def
    * @return - Documentation title from def name
    */
  def defAction(
    ast: AST.Def,
    partialDoc: AST.Comment.Documented
  ): AST.Comment.Documented = {
    AST.Comment.DocumentedOf[AST](Some(ast.name), partialDoc.doc)
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
        case v: AST.Comment.Documented =>
          // NOTE : Documented(before comment) -> Documentation
          val DocToLine = AST.Block.Line(Some(v), 0)
          val updatedWithDoc =
            astDoc.lines.toList.updated(elem._2 - 1, DocToLine)
          // NOTE : Documentation -> Infix (to get back func. def)
          val infix            = astBeginning.lines.toList(elem._2)
          val updatedWithInfix = updatedWithDoc.updated(elem._2, infix)

          astDoc = AST.Module(List1(updatedWithInfix).get)
        case _ =>
      }
    }
    astDoc
  }

  /** generateHTMLForEveryDocumentation - this method is used for generation of
    * HTML files from parsed and reformatted Documentation(s) and/or Documented(s)
    *
    * @param ast - parsed AST.Module and reformatted using Doc Parser
    */
  def generateHTMLForEveryDocumentation(ast: AST.Module): Unit = {
    ast.map { elem =>
      elem match {
        case v: AST.Comment.Documented =>
          new DocParser().onHTMLRendering(v)
        case _ =>
      }
      elem
    }
  }
}
