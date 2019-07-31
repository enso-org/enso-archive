package org.enso.syntax.text

import org.enso.flexer.Macro
import org.enso.syntax.text.DocAST._
import org.enso.syntax.text.docsParser.DocParserDef
import org.enso.{flexer => Flexer}
import org.scalatest._

class DocParserSpec extends FlatSpec with Matchers {
  val parserCons: Macro.Out[DocAST.AST] = Macro.compile(DocParserDef)

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
  "*Foo*" ?= Documentation(
    Synopsis(Section(0, TextBlock, Formatter(Bold, "Foo")))
  )
  "_Foo_" ?= Documentation(
    Synopsis(Section(0, TextBlock, Formatter(Italic, "Foo")))
  )
  "~Foo~" ?= Documentation(
    Synopsis(Section(0, TextBlock, Formatter(Strikethrough, "Foo")))
  )
  "`Foo`" ?= Documentation(Synopsis(Section(0, TextBlock, CodeLine("Foo"))))
  "~*Foo*~" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter(Strikethrough, Formatter(Bold, "Foo")))
    )
  )
  "~_Foo_~" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter(Strikethrough, Formatter(Italic, "Foo")))
    )
  )
  "_~Foo~_" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter(Italic, Formatter(Strikethrough, "Foo")))
    )
  )
  "_*Foo*_" ?= Documentation(
    Synopsis(Section(0, TextBlock, Formatter(Italic, Formatter(Bold, "Foo"))))
  )
  "*_Foo_*" ?= Documentation(
    Synopsis(Section(0, TextBlock, Formatter(Bold, Formatter(Italic, "Foo"))))
  )
  "*~Foo~*" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter(Bold, Formatter(Strikethrough, "Foo")))
    )
  )
  "_~*Foo*~_" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter(Italic, Formatter(Strikethrough, Formatter(Bold, "Foo")))
      )
    )
  )
  ///////////////////////////////
  ///// Unclosed formatters /////
  ///////////////////////////////
  "_*Foo*" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter.Unclosed(Italic, Formatter(Bold, "Foo")))
    )
  )
  "~*Foo*" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter.Unclosed(Strikethrough, Formatter(Bold, "Foo"))
      )
    )
  )
  "***Foo" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter(Bold), Formatter.Unclosed(Bold, "Foo"))
    )
  )
  "*_Foo_" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, Formatter.Unclosed(Bold, Formatter(Italic, "Foo")))
    )
  )
  "~_Foo_" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter.Unclosed(Strikethrough, Formatter(Italic, "Foo"))
      )
    )
  )
  "___Foo" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter(Italic),
        Formatter.Unclosed(Italic, "Foo")
      )
    )
  )
  "*~Foo~" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter.Unclosed(Bold, Formatter(Strikethrough, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter.Unclosed(Italic, Formatter(Strikethrough, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Formatter(Strikethrough),
        Formatter.Unclosed(Strikethrough, "Foo")
      )
    )
  )
  "`import foo`" ?= Documentation(
    Synopsis(Section(0, TextBlock, CodeLine("import foo")))
  )
  ////////////////////
  ///// Segments /////
  ////////////////////
  "!Important" ?= Documentation(Synopsis(Section(0, Important, "Important")))
  "?Info"      ?= Documentation(Synopsis(Section(0, Info, "Info")))
  ">Example"   ?= Documentation(Synopsis(Section(0, Example, "Example")))
  "?Info\n\n!Important" ?= Documentation(
    Synopsis(Section(0, Info, "Info", "\n")),
    Details(Section(0, Important, "Important"))
  )
  "?Info\n\n!Important\n\n>Example" ?= Documentation(
    Synopsis(
      Section(0, Info, "Info", "\n")
    ),
    Details(
      Section(0, Important, "Important", "\n"),
      Section(0, Example, "Example")
    )
  )
  /////////////////
  ///// Lists /////
  /////////////////
  "ul:\n  - Foo\n  - Bar" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        "ul:",
        "\n",
        ListBlock(2, Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Documentation(
    Synopsis(
      Section(0, TextBlock, "ol:", "\n", ListBlock(2, Ordered, " Foo", " Bar"))
    )
  )
  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item""".stripMargin ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
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
      Section(
        0,
        TextBlock,
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
      Section(
        0,
        TextBlock,
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
      Section(
        0,
        TextBlock,
        Link.URL(
          "Hello",
          "Http://Google.com"
        )
      )
    )
  )
  "![Media](http://foo.com)" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        Link.Image(
          "Media",
          "http://foo.com"
        )
      )
    )
  )
  /////////////////
  ///// other /////
  /////////////////
  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?= Documentation(
    Synopsis(
      Section(
        0,
        TextBlock,
        "Foo ",
        Formatter(Bold, "Foo"),
        " ",
        Formatter(Strikethrough, Formatter.Unclosed(Bold, "Bar")),
        " ",
        CodeLine("foo bar baz bo"),
        " ",
        "\n"
      )
    ),
    Details(
      Section(0, TextBlock, "Hello Section", "\n"),
      Section(0, Important, "important", "\n"),
      Section(0, Info, "Hi", "\n"),
      Section(0, Example, "Example")
    )
  )
  ////////////////
  ///// Tags /////
  ////////////////
  "DEPRECATED" ?= Documentation(Tags(TagClass(Deprecated)))
  "MODIFIED"   ?= Documentation(Tags(TagClass(Modified)))
  "ADDED"      ?= Documentation(Tags(TagClass(Added)))
  "REMOVED"    ?= Documentation(Tags(TagClass(Removed)))
  "REMOVED\nFoo" ?= Documentation(
    Tags(TagClass(Removed)),
    Synopsis(Section(0, TextBlock, "Foo"))
  )
  "DEPRECATED in 1.0" ?= Documentation(
    Tags(TagClass(Deprecated, "in 1.0"))
  )
  "DEPRECATED in 1.0\nMODIFIED" ?= Documentation(
    Tags(TagClass(Deprecated, "in 1.0"), TagClass(Modified))
  )
}
