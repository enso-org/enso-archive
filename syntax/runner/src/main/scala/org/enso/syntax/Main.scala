package org.enso.syntax
import org.enso.flexer.Macro.compile
import org.enso.flexer.Success
import org.enso.parser.Parser
import org.enso.flexer._
import org.enso.parser.AST

import org.enso.parser.docsParser.DocParser
import org.enso.parser.docsParser.DocAST

import scala.reflect.runtime.universe

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

////  val str = "a (b"
//  val str = "a\n b\n a" // .stripMargin
//  println(str)
//  val reader = new StringReader(str)
//  val ss     = new Lexer(reader)
//  val toks   = ss.lexAll()
//  var ttt    = 10
//  pprint(toks.toString)
//
//  val sparser = new SParser(new StringReader(str))
//
//  val bparser = new BParser(new StringReader(str))
//  val parser  = new ppp.Parser(new StringReader(str))
//
//  pprint(bparser.parse.toString())
//  pprint(parser.parse.toString())
//  pprint("!")
//  println(sparser.strInput)
//  pprint(sparser.parse.toString)
//
// '`('d`
//
// a = ('foo`bar)`baz)

  //////////////////////////////////////////////////////////////

//  @expand object Parser extends ParserBase[Int] {
//    def getResult() = Some(5)
//
//    override def initialize() = {}
//  }
//
//  println(Foo(7))

  val parserCons = compile(Parser)

  val p1 = parserCons()
  val p2 = parserCons()

  p1.bufferLen = 11

  println(p1.bufferLen)
  println(p2.bufferLen)

  val out = p1.run("a , b , c")
  out match {
    case Success(v, _) =>
      pprint(v)
      println(v.show())
      val spaceGroups = AST.Ops.partitionExprToSpaceGroups(
        v.asInstanceOf[AST.Module].firstLine.elem.get
      )
      pprint(spaceGroups)

      println("-----")
//      val out = AST.Ops.add2x(part.segs.head.expr)

      val flatExpr = spaceGroups.segs.map(s => AST.Ops.add2x(s.expr))

      println("----------------")
      val out = AST.Ops.add2x(flatExpr)
      pprint(out)
  }

//  import scala.reflect.runtime.universe._
//  val r = reify((new Foo().getClass))
//  println(r)
//
//  println(p1)
//  println(p1())
//
//  object A {
//    var x = 0;
//    def foo() = {
//      println(x)
//    }
//  }
//  (* a + b)
//  object B {
//    import A._
//    A
//  }
//
//  val b = new B()
//  println(B.a
//  val p2 = p1.run("'foo'")
//
////  val p1   = new Parser
////  val code = p1.specialize()
////  val p2   = p1.debugRun("'\\u12o45'")
//  pprint(p2)
//  p2 match {
//    case Success(v, _) =>
//      println(v.span)
//  }

//  println("CODE LEN:", code.length) //136500

  println()

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

  val docParserCons        = compile(DocParser)
  val docParserConsApplied = docParserCons()

  println(docParserConsApplied.bufferLen)

  val outDoc = docParserConsApplied.run(dataToParse)
  outDoc match {
    case Success(v, _) => {
      pprint(v)
      print(v.show())
      println("\n\n")
      print(v.generateHTML())
    }
  }
}
