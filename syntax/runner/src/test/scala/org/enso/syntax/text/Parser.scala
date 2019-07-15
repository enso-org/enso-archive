package org.enso.syntax.text.test

import org.enso.flexer.Macro
import org.enso.parser.AST._
import org.enso.parser.AST
import org.enso.parser.Parser
import org.enso.{flexer => Flexer}
import org.scalatest._

class LexerSpec extends FlatSpec with Matchers {

  val parserCons = Macro.compile(new Parser())

  def parse(input: String) = {
    val parser = parserCons()
    parser.run(input)
  }

  def assertModule(input: String, result: AST): Assertion = {
    val tt = parse(input)
    tt match {
      case Flexer.Success(value, offset) =>
        assert(value == result)
        assert(value.show() == input)
      case _ => fail(s"Parsing failed, consumed ${tt.offset} chars")
    }
  }

  def assertExpr(input: String, result: AST): Assertion = {
    val tt = parse(input)
    tt match {
      case Flexer.Success(value, offset) => {
        val module = value.asInstanceOf[Module]
        module.lines match {
          case Nil =>
            module.firstLine.elem match {
              case None => fail("Empty expression")
              case Some(e) => {
                assert(e == result)
                assert(value.show() == input)
              }
            }
          case _ => fail("Multi-line block")
        }
      }
      case _ => fail(s"Parsing failed, consumed ${tt.offset} chars")
    }
  }

  implicit class TestString(input: String) {
    def parseTitle(str: String): String = {
      val escape = (str: String) => str.replace("\n", "\\n")
      s"parse `${escape(str)}`"
    }

    private val testBase = it should parseTitle(input)

    def ?==(out: AST)    = testBase in { assertExpr(input, out) }
    def ?==(out: Module) = testBase in { assertModule(input, out) }
  }

  /////////////////
  // Identifiers //
  /////////////////

  "_"      ?== "_"
  "Name"   ?== "Name"
  "name"   ?== "name"
  "name'"  ?== "name'"
  "name''" ?== "name''"
  "name'a" ?== Identifier.InvalidSuffix("name'", "a")
  "name_"  ?== "name_"
  "name_'" ?== "name_'"
  "name'_" ?== Identifier.InvalidSuffix("name'", "_")
  "name`"  ?== "name" $ Unrecognized("`")

  ///////////////
  // Operators //
  ///////////////

  "++"   ?== Operator("++")
  "="    ?== Operator("=")
  "=="   ?== Operator("==")
  ":"    ?== Operator(":")
  ","    ?== Operator(",")
  "."    ?== Operator(".")
  ".."   ?== Operator("..")
  "..."  ?== Operator("...")
  ">="   ?== Operator(">=")
  "<="   ?== Operator("<=")
  "/="   ?== Operator("/=")
  "+="   ?== Modifier("+")
  "-="   ?== Modifier("-")
  "==="  ?== Identifier.InvalidSuffix("==", "=")
  "...." ?== Identifier.InvalidSuffix("...", ".")
  ">=="  ?== Identifier.InvalidSuffix(">=", "=")
  "+=="  ?== Identifier.InvalidSuffix("+", "==")

  /////////////////
  // Expressions //
  /////////////////

  "a b"       ?== ("a" $_ "b")
  "a + b"     ?== ("a" $_ "+" $_ "b")
  "()"        ?== Group()
  "(())"      ?== Group(Group())
  "(()"       ?== Group.Unclosed(Group())
  "(("        ?== Group.Unclosed(Group.Unclosed())
  "( "        ?== Group.Unclosed()
  ")"         ?== Group.UnmatchedClose
  ")("        ?== Group.UnmatchedClose $ Group.Unclosed()
  "a ( b c )" ?== ("a" $_ Group(1, "b" $_ "c", 1))
  "(a (b c))" ?== Group("a" $_ Group("b" $_ "c"))

  ////////////
  // Layout //
  ////////////

  ""      ?== Module(Line())
  "\n"    ?== Module(Line(), Line())
  "  \n " ?== Module(Line(2), Line(1))
  "\n\n"  ?== Module(Line(), Line(), Line())
  //  test module "(a)"  ==? GroupBegin  :: Var("a") :: GroupEnd
  //  test module "[a]"  ==? ListBegin   :: Var("a") :: ListEnd
  //  test module "{a}"  ==? RecordBegin :: Var("a") :: RecordEnd

  /////////////
  // Numbers //
  /////////////

  "7"     ?== 7
  "07"    ?== Number("07")
  "10_7"  ?== Number(10, 7)
  "16_ff" ?== Number(16, "ff")
  "16_"   ?== Number.DanglingBase("16")
  "7.5"   ?== 7 $ "." $ 5

  //////////
  // Text //
  //////////

  "'foo'" ?== Text("foo")
  //  "'"          , TextBegin   )
  //  "\""         , TextRawBegin)
  //  "''"         , TextBegin    :: TextEnd)
  //  "\"\""       , TextRawBegin :: TextRawEnd)
  //  "'''"        , TextBegin   )
  //  "\"\"\""     , TextRawBegin)
  //  "' '"        , TextBegin    :: Text(" ") :: TextEnd)
  //  "\" \""      , TextRawBegin :: Text(" ") :: TextRawEnd)
  //  "'' ''"      , TextBegin    :: TextEnd    :: TextBegin    :: TextEnd   )
  //  "\"\" \"\""  , TextRawBegin :: TextRawEnd :: TextRawBegin :: TextRawEnd)
  //  "'\n'"       , TextBegin    :: EOL :: TextEnd   )
  //  "\"\n\""     , TextRawBegin :: EOL :: TextRawEnd)
  //  "'\\\\'"     , TextBegin    :: TextEscape(SlashEscape) :: TextEnd)
  //  "\"\\\\\""   , TextRawBegin :: Text("\\") :: TextEscape(RawQuoteEscape))
  //  "'\\\''"     , TextBegin    :: TextEscape(QuoteEscape) :: TextEnd   )
  //  "\"\\\'\""   , TextRawBegin :: TextEscape(QuoteEscape) :: TextRawEnd)
  //  "'\\\"'"     , TextBegin    :: TextEscape(RawQuoteEscape) :: TextEnd   )
  //  "\"\\\"\""   , TextRawBegin :: TextEscape(RawQuoteEscape) :: TextRawEnd)
  //  "''' '''"            , TextBegin    :: Text(" ") :: TextEnd   )
  //  "\"\"\" \"\"\""      , TextRawBegin :: Text(" ") :: TextRawEnd)
  //  "''' '' '''"         , TextBegin    :: Text(" ") :: Text("''")   :: Text(" ") :: TextEnd   )
  //  "\"\"\" \"\" \"\"\"" , TextRawBegin :: Text(" ") :: Text("\"\"") :: Text(" ") :: TextRawEnd)
  //
  //  // Int Escapes
  //  expr("'\\12'"     , TextBegin    :: TextEscape(IntEscape(12)) :: TextEnd   )
  //  expr("\"\\12\""   , TextRawBegin :: Text("\\") :: Text("12")  :: TextRawEnd)
  //
  //  // Char Escapes
  //  expr("'\\a'"      , TextBegin :: TextEscape(CharEscape(7)) :: TextEnd)
  //  expr("'\\b'"      , TextBegin :: TextEscape(CharEscape(8)) :: TextEnd)
  //  expr("'\\f'"      , TextBegin :: TextEscape(CharEscape(12)) :: TextEnd)
  //  expr("'\\n'"      , TextBegin :: TextEscape(CharEscape(10)) :: TextEnd)
  //  expr("'\\r'"      , TextBegin :: TextEscape(CharEscape(13)) :: TextEnd)
  //  expr("'\\t'"      , TextBegin :: TextEscape(CharEscape(9)) :: TextEnd)
  //  expr("'\\v'"      , TextBegin :: TextEscape(CharEscape(11)) :: TextEnd)
  //  expr("'\\e'"      , TextBegin :: TextEscape(CharEscape(27)) :: TextEnd)
  //  expr("'\\q'"      , TextBegin :: TextEscape(InvalidCharEscape('q')) :: TextEnd)
  //  expr("\"\\a\""    , TextRawBegin :: Text("\\") :: Text("a") :: TextRawEnd)
  //
  //  // Control Escapes
  //  expr("'\\NUL'"    , TextBegin :: TextEscape(CtrlEscape(0)) :: TextEnd)
  //  expr("'\\SOH'"    , TextBegin :: TextEscape(CtrlEscape(1)) :: TextEnd)
  //  expr("'\\STX'"    , TextBegin :: TextEscape(CtrlEscape(2)) :: TextEnd)
  //  expr("'\\ETX'"    , TextBegin :: TextEscape(CtrlEscape(3)) :: TextEnd)
  //  expr("'\\EOT'"    , TextBegin :: TextEscape(CtrlEscape(4)) :: TextEnd)
  //  expr("'\\ENQ'"    , TextBegin :: TextEscape(CtrlEscape(5)) :: TextEnd)
  //  expr("'\\ACK'"    , TextBegin :: TextEscape(CtrlEscape(6)) :: TextEnd)
  //  expr("'\\BEL'"    , TextBegin :: TextEscape(CtrlEscape(7)) :: TextEnd)
  //  expr("'\\BS'"     , TextBegin :: TextEscape(CtrlEscape(8)) :: TextEnd)
  //  expr("'\\TAB'"    , TextBegin :: TextEscape(CtrlEscape(9)) :: TextEnd)
  //  expr("'\\LF'"     , TextBegin :: TextEscape(CtrlEscape(10)) :: TextEnd)
  //  expr("'\\VT'"     , TextBegin :: TextEscape(CtrlEscape(11)) :: TextEnd)
  //  expr("'\\FF'"     , TextBegin :: TextEscape(CtrlEscape(12)) :: TextEnd)
  //  expr("'\\CR'"     , TextBegin :: TextEscape(CtrlEscape(13)) :: TextEnd)
  //  expr("'\\SO'"     , TextBegin :: TextEscape(CtrlEscape(14)) :: TextEnd)
  //  expr("'\\SI'"     , TextBegin :: TextEscape(CtrlEscape(15)) :: TextEnd)
  //  expr("'\\DLE'"    , TextBegin :: TextEscape(CtrlEscape(16)) :: TextEnd)
  //  expr("'\\DC1'"    , TextBegin :: TextEscape(CtrlEscape(17)) :: TextEnd)
  //  expr("'\\DC2'"    , TextBegin :: TextEscape(CtrlEscape(18)) :: TextEnd)
  //  expr("'\\DC3'"    , TextBegin :: TextEscape(CtrlEscape(19)) :: TextEnd)
  //  expr("'\\DC4'"    , TextBegin :: TextEscape(CtrlEscape(20)) :: TextEnd)
  //  expr("'\\NAK'"    , TextBegin :: TextEscape(CtrlEscape(21)) :: TextEnd)
  //  expr("'\\SYN'"    , TextBegin :: TextEscape(CtrlEscape(22)) :: TextEnd)
  //  expr("'\\ETB'"    , TextBegin :: TextEscape(CtrlEscape(23)) :: TextEnd)
  //  expr("'\\CAN'"    , TextBegin :: TextEscape(CtrlEscape(24)) :: TextEnd)
  //  expr("'\\EM'"     , TextBegin :: TextEscape(CtrlEscape(25)) :: TextEnd)
  //  expr("'\\SUB'"    , TextBegin :: TextEscape(CtrlEscape(26)) :: TextEnd)
  //  expr("'\\ESC'"    , TextBegin :: TextEscape(CtrlEscape(27)) :: TextEnd)
  //  expr("'\\FS'"     , TextBegin :: TextEscape(CtrlEscape(28)) :: TextEnd)
  //  expr("'\\GS'"     , TextBegin :: TextEscape(CtrlEscape(29)) :: TextEnd)
  //  expr("'\\RS'"     , TextBegin :: TextEscape(CtrlEscape(30)) :: TextEnd)
  //  expr("'\\US'"     , TextBegin :: TextEscape(CtrlEscape(31)) :: TextEnd)
  //  expr("'\\DEL'"    , TextBegin :: TextEscape(CtrlEscape(127)) :: TextEnd)
  //  expr("\"\\NUL\""  , TextRawBegin :: Text("\\") :: Text("NUL") :: TextRawEnd)
  //
  //  // Unicode Escapes
  //  expr("'\\uF'"          , TextBegin :: TextEscape(InvalidCharEscape('u')) :: Text("F") :: TextEnd)
  //  expr("'\\uFF00'"       , TextBegin :: TextEscape(Uni16Escape(0xFF00)) :: TextEnd)
  //  expr("'\\U00ABCDEF'"   , TextBegin :: TextEscape(Uni32Escape(0x00ABCDEF)) :: TextEnd)
  //  expr("'\\UFFFFFFFF'"   , TextBegin :: TextEscape(InvalidUni32Escape("FFFFFFFF")) :: TextEnd)
  //  expr("'\\u{FF0}'"      , TextBegin :: TextEscape(Uni21Escape(0xFF0)) :: TextEnd)
  //  expr("'\\u{FFFFFFFF}'" , TextBegin :: TextEscape(InvalidUni21Escape("FFFFFFFF")) :: TextEnd)
  //  expr("'\\u{}'"         , TextBegin :: TextEscape(InvalidUni21Escape("")) :: TextEnd)
  //
  //  // Interpolation
  //  expr("'`{ }`'"        , TextBegin :: TextInterpolateBegin :: RecordBegin :: RecordEnd :: TextInterpolateEnd :: TextEnd)
  //  expr("'`{ }'"         , TextBegin :: TextInterpolateBegin :: RecordBegin :: RecordEnd :: TextBegin)
  //  expr("'`  `}'"        , TextBegin :: TextInterpolateBegin :: TextInterpolateEnd :: Text("}") :: TextEnd)
  //  expr("'`a`'"          , TextBegin :: TextInterpolateBegin :: Var("a") :: TextInterpolateEnd :: TextEnd)
  //  expr("'`'a'`'"        , TextBegin :: TextInterpolateBegin :: TextBegin :: Text("a") :: TextEnd :: TextInterpolateEnd :: TextEnd)
  //  expr("'''`'a'`'''"    , TextBegin :: TextInterpolateBegin :: TextBegin :: Text("a") :: TextEnd :: TextInterpolateEnd :: TextEnd)
  //  expr("'`'''a'''`'"    , TextBegin :: TextInterpolateBegin :: TextBegin :: Text("a") :: TextEnd :: TextInterpolateEnd :: TextEnd)
  //  expr("\"``\""         , TextRawBegin :: Text("``") :: TextRawEnd)
  //  expr("'`'`a`'`'"      , TextBegin :: TextInterpolateBegin :: TextBegin :: TextInterpolateBegin :: Var("a") :: TextInterpolateEnd :: TextEnd :: TextInterpolateEnd :: TextEnd)
  //
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
}
