package org.enso.syntax.text

import org.enso.flexer.Macro
import org.enso.syntax.text.DocAST._
import org.enso.syntax.text.docsParser.Definition
import org.enso.{flexer => Flexer}
import org.scalatest._

class DocParserSpec extends FlatSpec with Matchers {
  val parserCons: Macro.Out[DocAST.AST] = Macro.compile(Definition)

  def parse(input: String) = {
    val parser = parserCons()
    parser.run(input)
  }

  def assertExpr(input: String, result: DocAST.AST): Assertion = {
    val tt = parse(input)
    tt match {
      case Flexer.Success(value, _) => {
        println(s"\nresult : '$result'")
        println(s"value  : '$value'")
        println(s"\ninput  : '$input'")
        println(s"v.show : '${value.show()}'\n")
        //println(s"\nv.html : '${value.generateHTML()}'")
        assert(value == result)
        assert(value.show() == input)
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
    def ?=(out: DocAST.AST): Unit = testBase in { assertExpr(input, out) }
  }
  //////////////////////
  ///// Formatters /////
  //////////////////////
  "*Foo*" ?= Documentation(Synopsis(TextBlock(0, Formatter(Bold, "Foo"))))
  "_Foo_" ?= Documentation(Synopsis(TextBlock(0, Formatter(Italic, "Foo"))))
  "~Foo~" ?= Documentation(
    Synopsis(TextBlock(0, Formatter(Strikethrough, "Foo")))
  )
  "`Foo`" ?= Documentation(Synopsis(TextBlock(0, CodeLine("Foo", false))))
  "~*Foo*~" ?= Documentation(
    Synopsis(
      TextBlock(0, Formatter(Strikethrough, Formatter(Bold, "Foo")))
    )
  )
  "~_Foo_~" ?= Documentation(
    Synopsis(
      TextBlock(0, Formatter(Strikethrough, Formatter(Italic, "Foo")))
    )
  )
  "_~Foo~_" ?= Documentation(
    Synopsis(
      TextBlock(0, Formatter(Italic, Formatter(Strikethrough, "Foo")))
    )
  )
  "_*Foo*_" ?= Documentation(
    Synopsis(TextBlock(0, Formatter(Italic, Formatter(Bold, "Foo"))))
  )
  "*_Foo_*" ?= Documentation(
    Synopsis(TextBlock(0, Formatter(Bold, Formatter(Italic, "Foo"))))
  )
  "*~Foo~*" ?= Documentation(
    Synopsis(
      TextBlock(0, Formatter(Bold, Formatter(Strikethrough, "Foo")))
    )
  )
  "_~*Foo*~_" ?= Documentation(
    Synopsis(
      TextBlock(
        0,
        Formatter(Italic, Formatter(Strikethrough, Formatter(Bold, "Foo")))
      )
    )
  )
  ///////////////////////////////
  ///// Unclosed formatters /////
  ///////////////////////////////
  "_*Foo*" ?= Documentation(
    Synopsis(TextBlock(0, UnclosedFormatter(Italic, Formatter(Bold, "Foo"))))
  )
  "~*Foo*" ?= Documentation(
    Synopsis(
      TextBlock(0, UnclosedFormatter(Strikethrough, Formatter(Bold, "Foo")))
    )
  )
  "***Foo" ?= Documentation(
    Synopsis(TextBlock(0, Formatter(Bold), UnclosedFormatter(Bold, "Foo")))
  )
  "*_Foo_" ?= Documentation(
    Synopsis(TextBlock(0, UnclosedFormatter(Bold, Formatter(Italic, "Foo"))))
  )
  "~_Foo_" ?= Documentation(
    Synopsis(
      TextBlock(0, UnclosedFormatter(Strikethrough, Formatter(Italic, "Foo")))
    )
  )
  "___Foo" ?= Documentation(
    Synopsis(
      TextBlock(0, Formatter(Italic), UnclosedFormatter(Italic, "Foo"))
    )
  )
  "*~Foo~" ?= Documentation(
    Synopsis(
      TextBlock(0, UnclosedFormatter(Bold, Formatter(Strikethrough, "Foo")))
    )
  )
  "_~Foo~" ?= Documentation(
    Synopsis(
      TextBlock(0, UnclosedFormatter(Italic, Formatter(Strikethrough, "Foo")))
    )
  )
  "~~~Foo" ?= Documentation(
    Synopsis(
      TextBlock(
        0,
        Formatter(Strikethrough),
        UnclosedFormatter(Strikethrough, "Foo")
      )
    )
  )
  "`import foo`" ?= Documentation(
    Synopsis(TextBlock(0, CodeLine("import foo", false)))
  )
  ////////////////////
  ///// Segments /////
  ////////////////////
  "!Important" ?= Documentation(Synopsis(Important(0, "Important")))
  "?Info"      ?= Documentation(Synopsis(Info(0, "Info")))
  ">Example"   ?= Documentation(Synopsis(Example(0, "Example")))
  "?Info\n\n!Important" ?= Documentation(
    Synopsis(Info(0, "Info", "\n")),
    Body(Important(0, "Important"))
  )
  "?Info\n\n!Important\n\n>Example" ?= Documentation(
    Synopsis(
      Info(0, "Info", "\n")
    ),
    Body(
      Important(0, "Important", "\n"),
      Example(0, "Example")
    )
  )
  /////////////////
  ///// Lists /////
  /////////////////
  "ul:\n  - Foo\n  - Bar" ?= Documentation(
    Synopsis(
      TextBlock(0, "ul:", "\n", ListBlock(2, Unordered, " Foo", " Bar"))
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Documentation(
    Synopsis(
      TextBlock(0, "ol:", "\n", ListBlock(2, Ordered, " Foo", " Bar"))
    )
  )
  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item""".stripMargin ?= Documentation(
    Synopsis(
      TextBlock(
        0,
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
    |  - Fourth unordered item""".stripMargin ?= Documentation(
    Synopsis(
      TextBlock(
        0,
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
    |  - Fourth unordered item""".stripMargin ?= Documentation(
    Synopsis(
      TextBlock(
        0,
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
  "[Hello](Http://Google.com)" ?= Documentation(
    Synopsis(
      TextBlock(
        0,
        Link(
          "Hello",
          "Http://Google.com",
          URL
        )
      )
    )
  )
  "![Media](http://foo.com)" ?= Documentation(
    Synopsis(
      TextBlock(
        0,
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
  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?= Documentation(
    Synopsis(
      TextBlock(
        0,
        "Foo ",
        Formatter(Bold, "Foo"),
        " ",
        Formatter(Strikethrough, UnclosedFormatter(Bold, "Bar")),
        " ",
        CodeLine("foo bar baz bo", false),
        " ",
        "\n"
      )
    ),
    Body(
      TextBlock(0, "Hello Section", "\n"),
      Important(0, "important", "\n"),
      Info(0, "Hi", "\n"),
      Example(0, "Example")
    )
  )
  ////////////////
  ///// Tags /////
  ////////////////
  "DEPRECATED" ?= Documentation(
    Tags(Deprecated())
  )
  "MODIFIED" ?= Documentation(Tags(Modified()))
  "ADDED"    ?= Documentation(Tags(Added()))
  "REMOVED"  ?= Documentation(Tags(Removed()))
  "REMOVED\nFoo" ?= Documentation(
    Tags(Removed()),
    Synopsis(TextBlock(0, "Foo"))
  )
  "DEPRECATED in 1.0" ?= Documentation(
    Tags(Deprecated("in 1.0"))
  )
  "DEPRECATED in 1.0\nMODIFIED" ?= Documentation(
    Tags(Deprecated("in 1.0"), Modified())
  )
}
