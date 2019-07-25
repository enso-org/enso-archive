package org.enso.syntax.text

import org.enso.flexer.Macro.compile
import org.enso.flexer.Success
import org.enso.syntax.text.docsParser.Definition
import org.enso.syntax.text.DocAST
import org.enso.flexer

///////////////////
//// DocParser ////
///////////////////

class DocParser {
  import DocParser._
  private val engine = newEngine()

  def run(input: String): Result[DocAST.AST] = engine.run(input)
}

object DocParser {
  type Result[T] = flexer.Result[T]
  private val newEngine = compile(docsParser.Definition)
}

//////////////
//// Main ////
//////////////

object Main extends App {

  var indent = 0

  def pprint[T](t: T) {
    if (t == null) {
      println(t)
      return
    }
    val s = t.toString()
    print("  " * indent)
    val (l, r2) = s.span(x => x != '(' && x != ')')
    print(l)
    if (r2 == "") {
      println
      return
    }

    val (m, r) = r2.splitAt(1)

    if (m == "(") {
      indent += 1
      println(m)
      pprint(r)
    } else if (m == ")") {
      indent -= 1
      println(m)
      pprint(r)
    }

  }

  val parserCons = compile(Definition)

  val p1 = new DocParser()

  val dataToParse =
    """ DEPRECATED
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
      | ![Images are allowed as well](http://link-to-image.jpg).
      |
      | You can use ordered or unordered lists as well:
      |   - First unordered item
      |   - Second unordered item
      |     * First ordered sub item
      |     * Second ordered sub item
      |
      | ! Important
      |   An example wargning block. Use UIKit classes only from your app’s main thread
      |   or main dispatch queue, unless otherwise indicated. This restriction
      |   particularly applies to classes derived from UIResponder or that involve
      |   manipulating your app’s user interface in any way.
      |
      | ? An example info block.
      |   `Inline code is allowed everywhere`. It can span a single line only
      |
      |
      | A new section title is after 2 newlines
      | Now we can write a new
      |
      | > Title of an example
      |   This is an example displayed as a button in the docs. The first line is its
      |   name and this is its description. Code has to be indented.
      |       import Std.Math.Vector
      |       v = Vec3 1 2 'foo' : Vector (Int | String)
      |       print v """.stripMargin

  val out = p1.run(dataToParse)

  out match {
    case Success(v, _) =>
      pprint(v)
      println(v.show())
    //println(v.generateHTML()) TODO - As next task - work on HTML Generator and CSS
  }
  println()
}
