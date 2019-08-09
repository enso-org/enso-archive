package org.enso.syntax.text

import org.enso.flexer.ParserBase
import org.enso.syntax.text.AST.Block.Line
import org.enso.syntax.text.AST.Block.Line.Required
import org.enso.syntax.text.AST._
import org.enso.syntax.text.EDSL._
import org.enso.syntax.text.ast.Helpers._
import org.enso.{flexer => Flexer}
import org.scalatest._

class ParserSpec extends FlatSpec with Matchers {

  def parse(input: String) = {
    val parser = new Parser()
    parser.run(input)
  }

  def assertModule(input: String, result: AST): Assertion = {
    val output = parse(input)
    output match {
      case Flexer.Success(value, offset) =>
        assert(value == result)
        assert(value.show() == input)
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  def assertExpr(input: String, result: AST): Assertion = {
    val output = parse(input)
    output match {
      case Flexer.Success(value, offset) =>
        val module = value.asInstanceOf[Module]
        module.lines.tail match {
          case Nil =>
            module.lines.head.elem match {
              case None => fail("Empty expression")
              case Some(e) =>
                assert(e == result)
                assert(value.show() == input)
            }
          case _ => fail("Multi-line block")
        }
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  def assertIdentity(input: String): Assertion = {
    val output = parse(input)
    output match {
      case Flexer.Success(value, offset) =>
        assert(value.show() == input)
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

    def ?=(out: AST)    = testBase in { assertExpr(input, out) }
    def ?=(out: Module) = testBase in { assertModule(input, out) }
    def testIdentity = testBase in { assertIdentity(input) }
  }

  /////////////////////
  //// Identifiers ////
  /////////////////////

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

  ///////////////////
  //// Operators ////
  ///////////////////

  "++"   ?= "++"
  "="    ?= "="
  "=="   ?= "=="
  ":"    ?= ":"
  ","    ?= ","
  "."    ?= "."
  ".."   ?= ".."
  "..."  ?= "..."
  ">="   ?= ">="
  "<="   ?= "<="
  "/="   ?= "/="
  "+="   ?= Opr.Mod("+")
  "-="   ?= Opr.Mod("-")
  "==="  ?= Ident.InvalidSuffix("==", "=")
  "...." ?= Ident.InvalidSuffix("...", ".")
  ">=="  ?= Ident.InvalidSuffix(">=", "=")
  "+=="  ?= Ident.InvalidSuffix("+", "==")

  /////////////////////
  //// Expressions ////
  /////////////////////

  "a b"           ?= ("a" $_ "b")
  "a +  b"        ?= ("a" $_ "+") $__ "b"
  "a + b + c"     ?= ("a" $_ "+" $_ "b") $_ "+" $_ "c"
  "a , b , c"     ?= "a" $_ "," $_ ("b" $_ "," $_ "c")
  "a + b * c"     ?= "a" $_ "+" $_ ("b" $_ "*" $_ "c")
  "a * b + c"     ?= ("a" $_ "*" $_ "b") $_ "+" $_ "c"
  "a+ b"          ?= ("a" $ "+") $$_ "b"
  "a +b"          ?= "a" $_ ("+" $ "b")
  "a+ +b"         ?= ("a" $ "+") $$_ ("+" $ "b")
  "*a+"           ?= ("*" $ "a") $ "+"
  "+a*"           ?= "+" $ ("a" $ "*")
  "+ <$> a <*> b" ?= ("+" $_ "<$>" $_ "a") $_ "<*>" $_ "b"
  "+ * ^"         ?= App.Right("+", 1, App.Right("*", 1, "^"))
  "+ ^ *"         ?= App.Right("+", 1, App.Left("^", 1, "*"))
  "^ * +"         ?= App.Left(App.Left("^", 1, "*"), 1, "+")
  "* ^ +"         ?= App.Left(App.Right("*", 1, "^"), 1, "+")
  "^ + *"         ?= App.Infix("^", 1, "+", 1, "*")
  "* + ^"         ?= App.Infix("*", 1, "+", 1, "^")

  //  "a+b"    ?=
  //  "()"        ?= "(" $ ")" // Group()
  //  "(())"      ?= "(" $ "(" $ ")" $ ")" // Group(Group())
  //  "(()"       ?= "(" $ "(" $ ")" // Group.Unclosed(Group())
  //  "(("        ?= "(" $ "(" // Group.Unclosed(Group.Unclosed())
  //  "( "        ?= "(" // Group.Unclosed()
  //  ")"         ?= ")" // Group.UnmatchedClose
  //  ")("        ?= ")" $ "(" // Group.UnmatchedClose $ Group.Unclosed()
  //  "a ( b c )" ?= "a" $_ "(" $_ "b" $_ "c" $_ ")" // ("a" $_ Group(1, "b" $_ "c", 1))
  //  "(a (b c))" ?= "(" $ "a" $_ "(" $ "b" $_ "c" $ ")" $ ")" // Group("a" $_ Group("b" $_ "c"))

  //////////////////
  //// Comments ////
  //////////////////

  "foo   #L1NE"        ?= "foo" $___ Comment("L1NE")
  "#\n    L1NE\n LIN2" ?= Comment.MultiLine(0, List("", "   L1NE", "LIN2"))
  "#L1NE\nLIN2" ?= Module(
    Line(Comment.MultiLine(0, List("L1NE"))),
    Line(Cons("LIN2"))
  )

  ////////////////
  //// Layout ////
  ////////////////

  ""           ?= Module(Line())
  "\n"         ?= Module(Line(), Line())
  "\n\n"       ?= Module(Line(), Line(), Line())
  "  \n "      ?= Module(Line(2), Line(1))
  " \n  \n   " ?= Module(Line(1), Line(2), Line(3))


  "a b\n  c\n" ?= "a" $_ App(Var("b"),0,Block(2,List(),Required(Var("c"),0), List(Line(0))))

  "f =  \n\n\n".testIdentity
  "  \n\n\n f\nf".testIdentity
  "f =  \n\n  x ".testIdentity
  "f =\n\n  x\n\n y".testIdentity

  //  test module "(a)"  ==? GroupBegin  :: Var("a") :: GroupEnd
  //  test module "[a]"  ==? ListBegin   :: Var("a") :: ListEnd
  //  test module "{a}"  ==? RecordBegin :: Var("a") :: RecordEnd

  /////////////////
  //// Numbers ////
  /////////////////

  "7"     ?= 7
  "07"    ?= Number("07")
  "10_7"  ?= Number(10, 7)
  "16_ff" ?= Number(16, "ff")
  "16_"   ?= Number.DanglingBase("16")
  "7.5"   ?= App.Infix(7, 0, Opr("."), 0, 5)

  ////////////////////////
  //// UTF Surrogates ////
  ////////////////////////

  "\uD800\uDF1E" ?= Unrecognized("\uD800\uDF1E")

  //////////////
  //// Text ////
  //////////////

  "'"    ?= Text.Unclosed(Text())
  "''"   ?= Text()
  "'''"  ?= Text.Unclosed(Text(Text.Quote.Triple))
  "''''" ?= Text.Unclosed(Text(Text.Quote.Triple, "'"))
//  "'''''"   ?= Text.Unclosed(Text(Text.Quote.Triple, "''")) // FIXME
  "''''''"  ?= Text(Text.Quote.Triple)
  "'''''''" ?= Text(Text.Quote.Triple) $ Text.Unclosed(Text())
  "'a'"     ?= Text("a")
  "'a"      ?= Text.Unclosed(Text("a"))
  "'a'''"   ?= Text("a") $ Text()
  "'''a'''" ?= Text(Text.Quote.Triple, "a")
  "'''a'"   ?= Text.Unclosed(Text(Text.Quote.Triple, "a'"))
  //  "'''a''"  ?= Text.Unclosed(Text(Text.Quote.Triple, "a''")) // FIXME

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
  //  expr("#"              , Comment)
  //  expr("#c"             , Comment :: CommentBody("c"))
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

  //////////////////
  //// Mixfixes ////
  //////////////////

  //// Valid ////

  "()"                 ?= "(" II ")"
  "( )"                ?= "(" I_I ")"
  "( (  )   )"         ?= "(" I_ ("(" I__I ")") I___ ")"
  "(a)"                ?= "(" I "a" I ")"
  "((a))"              ?= "(" I ("(" I "a" I ")") I ")"
  "(((a)))"            ?= "(" I ("(" I ("(" I "a" I ")") I ")") I ")"
  "( (  a   )    )"    ?= "(" I_ ("(" I__ "a" I___ ")") I____ ")"
  "if a  then   b"     ?= "if" I1_ "a" I1__ "then" I1___ "b"
  "if a then b else c" ?= "if" I1_ "a" I1_ "then" I1_ "b" I1_ "else" I1_ "c"
  "(if a then  b) c"   ?= "(" I ("if" I1_ "a" I1_ "then" I1__ "b") I ")" $_ "c"
  "a (b c) d"          ?= "a" $_ ("(" I ("b" $_ "c") I ")") $_ "d"

  "(if a then b) else c" ?=
  "(" I ("if" I1_ "a" I1_ "then" I1_ "b") I ")" $_ "else" $_ "c"

  //// Invalid ////

  val _then_else = List(List("then"), List("then", "else"))

  "("           ?= "(" Ix ")"
  "(("          ?= "(" I ("(" Ix ")") Ix ")"
  "if"          ?= "if" Ixx (_then_else: _*)
  "(if a) then" ?= "(" I ("if" I_ "a" Ixx (_then_else: _*)) I ")" $_ "then"
  "if (a then)" ?= "if" I_ ("(" I ("a" $_ "then") I ")") Ixx (_then_else: _*)

//  "import Std.Math" ?= "foo"

  /////////////////////
  //// Large Input ////
  /////////////////////

  "OVERFLOW" * ParserBase.BUFFERSIZE testIdentity

  """
      a
     b
    c
   d
  e
  """.testIdentity

  """
  # pop1: adults
  # pop2: children
  # pop3: mutants
    Selects the 'fittest' individuals from population and kills the rest!

  keepBest : Pop -> Pop -> Pop -> Pop
  keepBest pop1 pop2 pop3 =

     unique xs
        = index xs 0 +: [1..length xs -1] . filter (isUnique xs) . map xs.at

     isUnique xs i ####
        = index xs i . score != index xs i-1 . score

     pop1<>pop2<>pop3 . sorted . unique . take (length pop1) . pure

  """.testIdentity

}
