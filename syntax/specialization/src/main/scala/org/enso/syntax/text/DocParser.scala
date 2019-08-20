package org.enso.syntax.text

import java.io.File
import java.io.PrintWriter

import org.enso.flexer
import org.enso.syntax.text.DocParser.Result
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.spec.DocParserDef
import scalatags.Text.TypedTag

///////////////////
//// DocParser ////
///////////////////

class DocParser {
  import DocParser._
  private val engine = newEngine()

  def run(input: String): Result[Doc] = engine.run(input)

}

object DocParser {
  type Result[T] = flexer.Parser.Result[T]
  private val newEngine = flexer.Parser.compile(DocParserDef())

  def run(input: String): Result[Doc] = new DocParser().run(input)
}

//////////////
/// Runner ///
//////////////
object DocParserRunner {
  val docParserInstance = new DocParser()

  val dataToParse =
    """ DEPRECATED
      | REMOVED - replaced by SwiftUI
      | Construct and manage a graphical, event-driven user interface for your iOS or
      | tvOS app.
      |
      | The UIKit framework provides the required infrastructure for your iOS or tvOS
      | apps. It provides the window and view architecture for implementing your
      | interface, the event handling infrastructure for delivering Multi-Touch and
      | other types of input to your app, and the main run loop needed to manage
      | interactions among the user, the system, and your app. Other features offered
      | by the framework include animation support, document support, drawing and
      | printing support, information about the current device, text management and
      | display, search support, accessibility support, app extension support, and
      | resource management. [Some inline link](http://google.com). *Bold test*,
      | or _italics_ are allowed. ~Strikethrough as well~. *_~Combined is funny~_*.
      | ![Images are allowed as well](https://github.com/luna/luna-studio/raw/master/resources/logo.ico).
      |
      | You can use ordered or unordered lists as well:
      |   - First unordered item
      |   - Second unordered item
      |     * First ordered sub item
      |     * Second ordered sub item
      |       
      | ! Important
      |   An example warning block. Use UIKit classes only from your app’s main thread
      |   or main dispatch queue, unless otherwise indicated. This restriction
      |   particularly applies to classes derived from UIResponder or that involve
      |   manipulating your app’s user interface in any way.
      |
      | ? An example info block.
      |   `Inline code is allowed everywhere`. It can span a single line only
      |
      |
      | A new section title *is* after 2 newlines
      | Now we can write a new
      |
      |
      | Invalid indent test:
      |   - First unordered item
      |   - Second unordered item
      |     * First ordered sub item
      |     * Second ordered sub item
      |   - Third unordered item
      |     * First ordered sub item
      |     * Second ordered sub item
      |       - First unordered sub item
      |       - Second unordered sub item
      |     * Third ordered sub item
      |    * Wrong Indent Item
      |   - Fourth unordered item
      |
      |
      | Invalid Formatter test
      | This is a *Unclosed test
      |
      | > Title of an example
      |   This is an example displayed as a button in the docs. The first line is its
      |   name and this is its description. Code has to be indented.
      |       import Std.Math.Vector
      |       v = Vec3 1 2 'foo' : Vector (Int | String)
      |       print v 
      |   Continuation of example section can occur after multiline code
      |""".stripMargin

  val docParserOut: Result[Doc] = docParserInstance.run(dataToParse)

  pprint.pprintln(docParserOut, width = 50, height = 10000)

  docParserOut match {
    case flexer.Parser.Result(_, flexer.Parser.Result.Success(v)) =>
      println("--- " * 20)
      println(v.show())
      println("--- " * 20)
      println(v.renderHTML())
      val path =
        "syntax/specialization/src/main/scala/org/enso/syntax/text/DocParserHTMLOut/"
      saveHTMLCodeToLocalFile(path, v.renderHTML())
  }

  def saveHTMLCodeToLocalFile(path: String, code: TypedTag[String]): Unit = {
    val writer = new PrintWriter(
      new File(
        path + "index.html"
      )
    )
    writer.write(code.toString)
    writer.close()
  }
}
