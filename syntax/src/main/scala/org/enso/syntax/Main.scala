package org.enso.syntax

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

  //////////////////////////////////////////////////////////////

  val p = new GeneratedParser
  p.run("16_ff00bb.f0")
  pprint(p.result)
  println(p.result.show)

}
