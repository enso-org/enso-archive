package org.enso.syntax.text

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.flexer.Parser.Result
import org.enso.flexer.Reader
import org.enso.syntax.text.AST.Block.Line
import org.enso.syntax.text.AST.Text.Segment.EOL
import org.enso.syntax.text.AST.Text.Segment.Plain
import org.enso.syntax.text.AST._
import org.enso.syntax.text.AST.implicits._
import org.enso.syntax.text.ast.DSL._
import org.scalatest._

class ParserSpec extends FlatSpec with Matchers {

  type Markers = Seq[(Int, Marker)]

  def assertModule(input: String, result: AST, markers: Markers): Assertion = {
    val parser = Parser()
    val output = parser.run(new Reader(input), markers)
    output match {
      case Result(offset, Result.Success(module)) =>
        val rmodule = parser.resolveMacros(module)
        assert(rmodule == result)
        assert(module.show() == new Reader(input).toString())
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  def assertExpr(input: String, result: AST, markers: Markers): Assertion = {
    val parser = Parser()
    val output = parser.run(new Reader(input), markers)
    output match {
      case Result(offset, Result.Success(module)) =>
        val rmodule = parser.resolveMacros(module)
        val tail    = module.lines.tail
        if (!tail.forall(_.elem.isEmpty)) fail("Multi-line block")
        else {
          rmodule.lines.head.elem match {
            case None => fail("Empty expression")
            case Some(e) =>
              assert(e == result)
              assert(module.show() == new Reader(input).toString())
          }
        }
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  def assertIdentity(input: String): Assertion = {
    val output = Parser().run(new Reader(input))
    output match {
      case Result(offset, Result.Success(value)) =>
        assert(value.show() == new Reader(input).toString())
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  implicit class TestString(input: String) {
    def parseTitle(str: String): String = {
      val maxChars = 20
      val escape   = (str: String) => str.replace("\n", "\\n")
      val str2     = escape(str)
      val str3 =
        if (str2.length < maxChars) str2
        else str2.take(maxChars) + "..."
      s"parse `$str3`"
    }

    private val testBase = it should parseTitle(input)

    def ?=(out: AST)    = testBase in { assertExpr(input, out, Seq()) }
    def ?=(out: Module) = testBase in { assertModule(input, out, Seq()) }
    def ?#=(out: AST) = testBase in { assertExpr(input, out, markers) }
    def testIdentity  = testBase in { assertIdentity(input) }
  }

  val markers = 0 to 100 map (offset => offset -> Marker(offset))

  //////////////////////////////////////////////////////////////////////////////
  //// Identifiers /////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "_"      ?= "_"
  "Name"   ?= "Name"
  "name"   ?= "name"
  "name'"  ?= "name'"
  "name''" ?= "name''"
  "name'a" ?= Ident.InvalidSuffix("name'", "a")
  "name_"  ?= "name_"
  "name_'" ?= "name_'"
  "name'_" ?= Ident.InvalidSuffix("name'", "_")
  "name`"  ?= "name" $ Unrecognized("`")

  //////////////////////////////////////////////////////////////////////////////
  //// Operators ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //  "="    ?= App.Sides("=")
  //  "#="   ?= App.Sides("#=")
  //  "##"   ?= App.Sides("##")
  "++"   ?= App.Sides("++")
  "=="   ?= App.Sides("==")
  ":"    ?= App.Sides(":")
  ","    ?= App.Sides(",")
  "."    ?= App.Sides(".")
  ".."   ?= App.Sides("..")
  "..."  ?= App.Sides("...")
  ">="   ?= App.Sides(">=")
  "<="   ?= App.Sides("<=")
  "/="   ?= App.Sides("/=")
  "+="   ?= Opr.Mod("+")
  "-="   ?= Opr.Mod("-")
  "==="  ?= Ident.InvalidSuffix("==", "=")
  "...." ?= Ident.InvalidSuffix("...", ".")
  ">=="  ?= Ident.InvalidSuffix(">=", "=")
  "+=="  ?= Ident.InvalidSuffix("+", "==")

  //////////////////////////////////////////////////////////////////////////////
  //// Precedence + Associativity //////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "a b"            ?= ("a" $_ "b")
  "a +  b"         ?= ("a" $_ "+") $__ "b"
  "a + b + c"      ?= ("a" $_ "+" $_ "b") $_ "+" $_ "c"
  "a , b , c"      ?= "a" $_ "," $_ ("b" $_ "," $_ "c")
  "a + b * c"      ?= "a" $_ "+" $_ ("b" $_ "*" $_ "c")
  "a * b + c"      ?= ("a" $_ "*" $_ "b") $_ "+" $_ "c"
  "a+ b"           ?= ("a" $ "+") $$_ "b"
  "a +b"           ?= "a" $_ ("+" $ "b")
  "a+ +b"          ?= ("a" $ "+") $$_ ("+" $ "b")
  "*a+"            ?= ("*" $ "a") $ "+"
  "+a*"            ?= "+" $ ("a" $ "*")
  "+ <$> a <*> b"  ?= (App.Sides("+") $_ "<$>" $_ "a") $_ "<*>" $_ "b"
  "+ * ^"          ?= App.Right("+", 1, App.Right("*", 1, App.Sides("^")))
  "+ ^ *"          ?= App.Right("+", 1, App.Left(App.Sides("^"), 1, "*"))
  "^ * +"          ?= App.Left(App.Left(App.Sides("^"), 1, "*"), 1, "+")
  "* ^ +"          ?= App.Left(App.Right("*", 1, App.Sides("^")), 1, "+")
  "^ + *"          ?= App.Infix(App.Sides("^"), 1, "+", 1, App.Sides("*"))
  "* + ^"          ?= App.Infix(App.Sides("*"), 1, "+", 1, App.Sides("^"))
  "a = b.c.d = 10" ?= "a" $_ "=" $_ (("b" $ "." $ "c" $ "." $ "d") $_ "=" $_ 10)
  "v = f x=1 y=2"  ?= "v" $_ "=" $_ ("f" $_ ("x" $_ "=" $_ 1) $_ ("y" $_ "=" $_ 2))
  "v' = v .x=1"    ?= "v'" $_ "=" $_ ("v" $_ ("." $ "x" $_ "=" $_ 1))

  //////////////////////////////////////////////////////////////////////////////
  //// Arrows //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "a -> b"      ?= "a" $_ "->" $_ "b"
  "a -> b -> c" ?= "a" $_ "->" $_ ("b" $_ "->" $_ "c")
  "a b -> c d"  ?= ("a" $_ "b") $_ "->" $_ ("c" $_ "d")
  "a b-> c d"   ?= "a" $_ ("b" $_ "->" $_ ("c" $_ "d"))

  //////////////////////////////////////////////////////////////////////////////
  //// Layout //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  ""           ?= Module(Line())
  "\n"         ?= Module(Line(), Line())
  "  \n "      ?= Module(Line(2), Line(1))
  "\n\n"       ?= Module(Line(), Line(), Line())
  " \n  \n   " ?= Module(Line(1), Line(2), Line(3))

  //////////////////////////////////////////////////////////////////////////////
  //// Numbers /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "7"     ?= 7
  "07"    ?= Number("07")
  "10_7"  ?= Number(10, 7)
  "16_ff" ?= Number(16, "ff")
  "16_"   ?= Number.DanglingBase("16")
  "7.5"   ?= App.Infix(7, 0, Opr("."), 0, 5)

  //////////////////////////////////////////////////////////////////////////////
  //// UTF Surrogates //////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "\uD800\uDF1E" ?= Unrecognized("\uD800\uDF1E")

  //////////////////////////////////////////////////////////////////////////////
  //// Text ////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //////////////
  //// Text ////
  //////////////

  "'"       ?= Text.Unclosed(Text())
  "''"      ?= Text()
  "'''"     ?= Text.Unclosed(Text(Text.Quote.Triple))
  "''''"    ?= Text.Unclosed(Text(Text.Quote.Triple, "'"))
  "'''''"   ?= Text.Unclosed(Text(Text.Quote.Triple, "''"))
  "''''''"  ?= Text(Text.Quote.Triple)
  "'''''''" ?= Text(Text.Quote.Triple) $ Text.Unclosed(Text())
  "'a'"     ?= Text("a")
  "'a"      ?= Text.Unclosed(Text("a"))
  "'a'''"   ?= Text("a") $ Text()
  "'''a'''" ?= Text(Text.Quote.Triple, "a")
  "'''a'"   ?= Text.Unclosed(Text(Text.Quote.Triple, "a'"))
  "'''a''"  ?= Text.Unclosed(Text(Text.Quote.Triple, "a''"))

  "\""             ?= Text.Unclosed(Text.Raw())
  "\"\""           ?= Text.Raw()
  "\"\"\""         ?= Text.Unclosed(Text.Raw(Text.Quote.Triple))
  "\"\"\"\""       ?= Text.Unclosed(Text.Raw(Text.Quote.Triple, "\""))
  "\"\"\"\"\""     ?= Text.Unclosed(Text.Raw(Text.Quote.Triple, "\"\""))
  "\"\"\"\"\"\""   ?= Text.Raw(Text.Quote.Triple)
  "\"\"\"\"\"\"\"" ?= Text.Raw(Text.Quote.Triple) $ Text.Unclosed(Text.Raw())
  "\"a\""          ?= Text.Raw("a")
  "\"a"            ?= Text.Unclosed(Text.Raw("a"))
  "\"a\"\"\""      ?= Text.Raw("a") $ Text.Raw()
  "\"\"\"a\"\"\""  ?= Text.Raw(Text.Quote.Triple, "a")
  "\"\"\"a\""      ?= Text.Unclosed(Text.Raw(Text.Quote.Triple, "a\""))
  "\"\"\"a\"\""    ?= Text.Unclosed(Text.Raw(Text.Quote.Triple, "a\"\""))

  "'''\nX\n Y\n'''" ?= Text.MultiLine(
    0,
    '\'',
    Text.Quote.Triple,
    List(EOL(), Plain("X"), EOL(), Plain(" Y"), EOL())
  )

  //// Escapes ////

  Text.Segment.Escape.Character.codes.foreach(i => s"'\\$i'" ?= Text(i))
  Text.Segment.Escape.Control.codes.foreach(i => s"'\\$i'"   ?= Text(i))

  "'\\\\'"   ?= Text(Text.Segment.Escape.Slash)
  "'\\''"    ?= Text(Text.Segment.Escape.Quote)
  "'\\\"'"   ?= Text(Text.Segment.Escape.RawQuote)
  "'\\"      ?= Text.Unclosed(Text("\\"))
  "'\\c'"    ?= Text(Text.Segment.Escape.Invalid("c"))
  "'\\cd'"   ?= Text(Text.Segment.Escape.Invalid("c"), "d")
  "'\\123d'" ?= Text(Text.Segment.Escape.Number(123), "d")

  //// Interpolation ////

  "'a`b`c'" ?= Text("a", Text.Segment.Interpolation(Some("b")), "c")
  "'a`b 'c`d`e' f`g'" ?= {
    val bd = "b" $_ Text("c", Text.Segment.Interpolation(Some("d")), "e") $_ "f"
    Text("a", Text.Segment.Interpolation(Some(bd)), "g")
  }
  //  "'`a(`'" ?= Text(Text.Segment.Interpolated(Some("a" $ Group.Unclosed())))
  //  // Comments
//    expr("#"              , Comment)
//    expr("#c"             , Comment :: CommentBody("c"))
  //  expr("#c\na"          , Comment :: CommentBody("c") :: EOL :: Var("a"))
  //  expr("#c\n a"         , Comment :: CommentBody("c") :: EOL :: CommentBody(" a"))
  //  expr(" #c\n a"        , Comment :: CommentBody("c") :: EOL :: Var("a"))
  //  expr(" #c\n  a"       , Comment :: CommentBody("c") :: EOL :: CommentBody("  a"))
  //  expr("a#c"            , Var("a") :: Comment :: CommentBody("c"))
  //  expr("a # c"          , Var("a") :: Comment :: CommentBody(" c"))
  //  expr("a#"             , Var("a") :: Comment)
  //  expr("a#\nb"          , Var("a") :: Comment :: EOL :: Var("b"))
  //  expr("a#\n b"         , Var("a") :: Comment :: EOL :: CommentBody(" b"))
  //
  //  // Disabled
  //  expr("a #= b"         , Var("a") :: DisabledAssignment :: Var("b"))
  //

  //////////////////////////////////////////////////////////////////////////////
  //// Markers /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "marked" ?#= Marked(Marker(0), Var("marked"))
  "Marked" ?#= Marked(Marker(0), Cons("Marked"))
  "111111" ?#= Marked(Marker(0), Number(111111))
  "'    '" ?#= Marked(Marker(0), Text("    "))
  "++++++" ?#= Marked(Marker(0), Opr("++++++"))
  "+++++=" ?#= Marked(Marker(0), Opr.Mod("+++++"))
  "a b  c" ?#= Marked(Marker(0), Var("a")) $_ Marked(Marker(2), Var("b")) $__ Marked(
    Marker(5),
    Var("c")
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Comments ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "foo   #L1"      ?= "foo" $___ Comment.SingleLine("L1")
  "#\n    L1\n L2" ?= Comment.MultiLine(0, List("", "    L1", " L2"))
  "#L1\nL2"        ?= Module(Line(Comment.SingleLine("L1")), Line(Cons("L2")))

  //////////////////////////////////////////////////////////////////////////////
  //// Flags/// ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "a #= b c" ?= "a" $_ "#=" $_ ("b" $_ "c")

  //////////////////////////////////////////////////////////////////////////////
  //// Blocks //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

//  "foo  \n bar" ?= "foo" $__ Block(1, "bar")
//
//  "f =  \n\n\n".testIdentity
//  "  \n\n\n f\nf".testIdentity
//  "f =  \n\n  x ".testIdentity
//  "f =\n\n  x\n\n y".testIdentity
//
//  "a b\n  c\n" ?= "a" $_ App(
//    Var("b"),
//    0,
//    Block(2, List(), Required(Var("c"), 0), List(Line()))
//  )

  //////////////////////////////////////////////////////////////////////////////
  //// Mixfixes ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  def amb(head: AST, lst: List[List[AST]]): Macro.Ambiguous =
    Macro.Ambiguous(Macro.Ambiguous.Segment(head), Tree(lst.map(_ -> (())): _*))

  def amb(head: AST, lst: List[List[AST]], body: SAST): Macro.Ambiguous =
    Macro.Ambiguous(
      Macro.Ambiguous.Segment(head, Some(body)),
      Tree(lst.map(_ -> (())): _*)
    )

  def _amb_group_(i: Int)(t: AST): Macro.Ambiguous =
    amb("(", List(List(")")), Shifted(i, t))

  val amb_group   = _amb_group_(0)(_)
  val amb_group_  = _amb_group_(1)(_)
  val amb_group__ = _amb_group_(2)(_)
  def group_(): Macro.Ambiguous = amb("(", List(List(")")))

  def _amb_if(i: Int)(t: AST) =
    amb("if", List(List("then"), List("then", "else")), Shifted(i, t))

  val amb_if   = _amb_if(0)(_)
  val amb_if_  = _amb_if(1)(_)
  val amb_if__ = _amb_if(2)(_)

  "()"          ?= Group()
  "( )"         ?= Group()
  "( (  )   )"  ?= Group(Group())
  "(a)"         ?= Group("a")
  "((a))"       ?= Group(Group("a"))
  "(((a)))"     ?= Group(Group(Group("a")))
  "( (  a   ))" ?= Group(Group("a"))
  "("           ?= amb("(", List(List(")")))
  "(("          ?= amb_group(group_())

  "import Std .  Math  .Vector".stripMargin ?= Import("Std", "Math", "Vector")

  """def Maybe a
    |    def Just val:a
    |    def Nothing
    |""".stripMargin ?= {
    val defJust    = Def("Just", List("val" $ ":" $ "a"))
    val defNothing = Def("Nothing")
    Def(
      "Maybe",
      List("a"),
      Some(Block(Block.Continuous, 4, defJust, Some(defNothing), None))
    )
  }

  """foo ->
    |    bar
    |""".stripMargin ?= "foo" $_ "->" $_ Block(
    Block.Discontinuous,
    4,
    "bar",
    None
  )

  "if a then b" ?= Mixfix(
    List1[AST.Ident]("if", "then"),
    List1[AST](" a ", "b")
  )
  "if a then b else c" ?= Mixfix(
    List1[AST.Ident]("if", "then", "else"),
    List1[AST]("a", "b", "c")
  )

  "if a"          ?= amb_if_("a": AST)
  "(if a) b"      ?= Group(amb_if_("a": AST)) $_ "b"
  "if (a then b " ?= amb_if_(amb_group("a" $_ "then" $_ "b"))

  //////////////////////////////////////////////////////////////////////////////
  //// Foreign /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val pyLine1 = "import re"
  val pyLine2 = """re.match(r"[^@]+@[^@]+\.[^@]+", "foo@ds.pl") != None"""
  s"""validateEmail address = foreign Python3
     |    $pyLine1
     |    $pyLine2
  """.stripMargin ?= ("validateEmail" $_ "address") $_ "=" $_
  Foreign(4, "Python3", List(pyLine1, pyLine2))

  //////////////////////////////////////////////////////////////////////////////
  //// Large Input /////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  ("(" * 10000).testIdentity
  ("OVERFLOW " * 2000).testIdentity
  ("\uD800\uDF1E " * 2000).testIdentity

  //////////////////////////////////////////////////////////////////////////////
  //// OTHER (TO BE PARTITIONED)////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "\na \nb \n".testIdentity
  "f =  \n\n\n".testIdentity
  "  \n\n\n f\nf".testIdentity
  "f =  \n\n  x ".testIdentity
  "  a\n   b\n  c".testIdentity
  "f =\n\n  x\n\n y".testIdentity

  """
    a
     b
   c
    d
  e
   f g h
  """.testIdentity

  """
  # pop1: adults
  # pop2: children
  # pop3: mutants
    Selects the 'fittest' individuals from population and kills the rest!

  log
  '''
  keepBest
  `pop1`
  `pop2`
  `pop3`
  '''

  unique xs
    = xs.at(0.0) +: [1..length xs -1] . filter (isUnique xs) . map xs.at

  isUnique xs i ####
    = xs.at(i).score != xs.at(i-1).score

  pop1<>pop2<>pop3 . sorted . unique . take (length pop1) . pure
  """.testIdentity


  ///////////////////////
  //// Preprocessing ////
  ///////////////////////

  "\t"   ?= Module(Line(4))
  "\r"   ?= Module(Line(), Line())
  "\r\n" ?= Module(Line(), Line())

}

////////////////////////////////////////////////////////////////////////////////
// TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO //
////////////////////////////////////////////////////////////////////////////////

// [ ] Layout parsing fixes [PR review]
// [ ] Some benchmarks are sometimes failing?
// [ ] Benchmarks are slower now - readjust (maybe profile later)
// [ ] operator blocks
// [ ] warnings in scala code
// [ ] Comments parsing
