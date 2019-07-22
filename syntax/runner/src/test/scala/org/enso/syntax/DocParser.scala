package org.enso.syntax.text

import org.enso.flexer.Macro
import org.enso.parser.docsParser.DocAST._
import org.enso.parser.docsParser.DocAST
import org.enso.parser.docsParser.DocParser
import org.enso.{flexer => Flexer}
import org.scalatest._

class DocParserSpec extends FlatSpec with Matchers {

  val parserCons = Macro.compile(DocParser)

  def parse(input: String) = {
    val parser = parserCons()
    parser.run(input)
  }

  def assertExpr(input: String, result: AST): Assertion = {
    val tt = parse(input)
    tt match {
      case Flexer.Success(value, offset) => {
        println(s"result : '${result}'")
        println(s"value  : '${value}'")

        println(s"input  : '${input}'")

        // FIXME - still need to fix non-displaying unicode chars
        val in    = input.replaceAll("\\p{C}", "")
        val vShow = value.show().replaceAll("\\p{C}", "")

        println(s"v.show : '${vShow}'")
        println(s"TEST EQ: '${vShow == in}'")

        vShow.zip(in).foreach {
          case (c1, c2) =>
            println(s"TEST '${c1}' '${c2}' = '${c1 == c2}'")
        }

        assert(value == result)
        assert(vShow == in)
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

    def ?==(out: AST): Unit = testBase in { assertExpr(input, out) }
  }
  //////////////////////
  ///// Formatters /////
  //////////////////////

  "*Foo*" ?== Documentation(Synopsis(TextBlock(Formatter(Bold, "Foo"))))
  "_Foo_" ?== Documentation(Synopsis(TextBlock(Formatter(Italic, "Foo"))))
  "~Foo~" ?== Documentation(
    Synopsis(TextBlock(Formatter(Strikethrough, "Foo")))
  )

  "`Foo`" ?== Documentation(Synopsis(TextBlock(CodeLine("Foo"))))

  "~*Foo*~" ?== Documentation(
    Synopsis(
      TextBlock(Formatter(Strikethrough, Formatter(Bold, "Foo")))
    )
  )
  "~_Foo_~" ?== Documentation(
    Synopsis(
      TextBlock(Formatter(Strikethrough, Formatter(Italic, "Foo")))
    )
  )
  "_~Foo~_" ?== Documentation(
    Synopsis(
      TextBlock(Formatter(Italic, Formatter(Strikethrough, "Foo")))
    )
  )
  "_*Foo*_" ?== Documentation(
    Synopsis(TextBlock(Formatter(Italic, Formatter(Bold, "Foo"))))
  )
  "*_Foo_*" ?== Documentation(
    Synopsis(TextBlock(Formatter(Bold, Formatter(Italic, "Foo"))))
  )
  "*~Foo~*" ?== Documentation(
    Synopsis(
      TextBlock(Formatter(Bold, Formatter(Strikethrough, "Foo")))
    )
  )

  "_~*Foo*~_" ?== Documentation(
    Synopsis(
      TextBlock(
        Formatter(Italic, Formatter(Strikethrough, Formatter(Bold, "Foo")))
      )
    )
  )
  ///////////////////////////////
  ///// Unclosed formatters /////
  ///////////////////////////////

  "_*Foo*" ?== Documentation(
    Synopsis(TextBlock(UnclosedFormatter(Italic, Formatter(Bold, "Foo"))))
  )
  "~*Foo*" ?== Documentation(
    Synopsis(
      TextBlock(UnclosedFormatter(Strikethrough, Formatter(Bold, "Foo")))
    )
  )
  "***Foo" ?== Documentation(
    Synopsis(TextBlock(Formatter(Bold), UnclosedFormatter(Bold, "Foo")))
  )

  "*_Foo_" ?== Documentation(
    Synopsis(TextBlock(UnclosedFormatter(Bold, Formatter(Italic, "Foo"))))
  )
  "~_Foo_" ?== Documentation(
    Synopsis(
      TextBlock(UnclosedFormatter(Strikethrough, Formatter(Italic, "Foo")))
    )
  )
  "___Foo" ?== Documentation(
    Synopsis(
      TextBlock(Formatter(Italic), UnclosedFormatter(Italic, "Foo"))
    )
  )

  "*~Foo~" ?== Documentation(
    Synopsis(
      TextBlock(UnclosedFormatter(Bold, Formatter(Strikethrough, "Foo")))
    )
  )

  "_~Foo~" ?== Documentation(
    Synopsis(
      TextBlock(UnclosedFormatter(Italic, Formatter(Strikethrough, "Foo")))
    )
  )

  "~~~Foo" ?== Documentation(
    Synopsis(
      TextBlock(
        Formatter(Strikethrough),
        UnclosedFormatter(Strikethrough, "Foo")
      )
    )
  )

  "`import foo`" ?== Documentation(Synopsis(TextBlock(CodeLine("import foo"))))

  ////////////////////
  ///// Segments /////
  ////////////////////

  "!Important" ?== Documentation(Synopsis(Important("Important")))
  "?Info"      ?== Documentation(Synopsis(Info("Info")))
  ">Example"   ?== Documentation(Synopsis(Example("Example")))
  "?Info\n\n!Important" ?== Documentation(
    Synopsis(Info("Info", "\n")),
    Body(Important("Important"))
  )
  "?Info\n\n!Important\n\n>Example" ?== Documentation(
    Synopsis(
      Info("Info", "\n")
    ),
    Body(
      Important("Important", "\n"),
      Example("Example")
    )
  )

  /////////////////
  ///// Lists /////
  /////////////////

  "ul:\n  - Foo\n  - Bar" ?== Documentation(
    Synopsis(
      TextBlock(
        "ul:",
        "\n",
        ListBlock(2, Unordered, " Foo", " Bar")
      )
    )
  )

  "ol:\n  * Foo\n  * Bar" ?== Documentation(
    Synopsis(
      TextBlock(
        "ol:",
        "\n",
        ListBlock(2, Ordered, " Foo", " Bar")
      )
    )
  )

  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item""".stripMargin ?== Documentation(
    Synopsis(
      TextBlock(
        "List",
        "\n",
        ListBlock(
          2,
          Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item"
        )
      )
    )
  )

  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |      - First unordered sub item
    |      - Second unordered sub item
    |    * Third ordered sub item
    |  - Fourth unordered item""".stripMargin ?== Documentation(
    Synopsis(
      TextBlock(
        "List",
        "\n",
        ListBlock(
          2,
          Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          ListBlock(
            4,
            Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            ListBlock(
              6,
              Unordered,
              " First unordered sub item",
              " Second unordered sub item"
            ),
            " Third ordered sub item"
          ),
          " Fourth unordered item"
        )
      )
    )
  )

  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |      - First unordered sub item
    |      - Second unordered sub item
    |    * Third ordered sub item
    |   * Wrong Indent Item
    |  - Fourth unordered item""".stripMargin ?== Documentation(
    Synopsis(
      TextBlock(
        "List",
        "\n",
        ListBlock(
          2,
          Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          ListBlock(
            4,
            Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            ListBlock(
              6,
              Unordered,
              " First unordered sub item",
              " Second unordered sub item"
            ),
            " Third ordered sub item",
            InvalidIndent(3, " Wrong Indent Item", Ordered)
          ),
          " Fourth unordered item"
        )
      )
    )
  )

  /////////////////
  ///// Links /////
  /////////////////

  "[Hello](Http://Google.com)" ?== Documentation(
    Synopsis(
      TextBlock(
        Link(
          "Hello",
          "Http://Google.com",
          URL
        )
      )
    )
  )
  "![Media](http://foo.com)" ?== Documentation(
    Synopsis(
      TextBlock(
        Link(
          "Media",
          "http://foo.com",
          Image
        )
      )
    )
  )
  /////////////////
  ///// other /////
  /////////////////

  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?== Documentation(
    Synopsis(
      TextBlock(
        "Foo ",
        Formatter(Bold, "Foo"),
        " ",
        Formatter(Strikethrough, UnclosedFormatter(Bold, "Bar")),
        " ",
        CodeLine("foo bar baz bo"),
        " ",
        "\n"
      )
    ),
    Body(
      TextBlock("Hello Section", "\n"),
      Important("important", "\n"),
      Info("Hi", "\n"),
      Example("Example")
    )
  )

  ////////////////
  ///// Tags /////
  ////////////////

  "DEPRECATED" ?== Documentation(
    Tags(Deprecated())
  )
  "MODIFIED" ?== Documentation(Tags(Modified()))
  "ADDED"    ?== Documentation(Tags(Added()))
  "REMOVED"  ?== Documentation(Tags(Removed()))
  "REMOVED\nFoo" ?== Documentation(
    Tags(Removed()),
    Synopsis(TextBlock("Foo"))
  )

  "DEPRECATED in 1.0" ?== Documentation(
    Tags(Deprecated("in 1.0"))
  )
  "DEPRECATED in 1.0\nMODIFIED" ?== Documentation(
    Tags(Deprecated("in 1.0"), Modified())
  )
}
