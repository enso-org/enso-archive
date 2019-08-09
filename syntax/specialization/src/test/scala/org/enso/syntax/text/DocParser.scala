package org.enso.syntax.text

import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc.AST._
import org.enso.Logger
import org.enso.{flexer => Flexer}
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Assertion

class DocParserSpec extends FlatSpec with Matchers {
  val logger = new Logger()
  def parse(input: String) = {
    val parser = new DocParser()
    parser.run(input)
  }

  def assertExpr(input: String, result: Doc.AST): Assertion = {
    val tt = parse(input)
    tt match {
      case Flexer.Success(value, _) => {
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
    def ?=(out: Doc.AST): Unit = testBase in { assertExpr(input, out) }
  }
  //////////////////////
  ///// Formatters /////
  //////////////////////
  "*Foo*" ?= Doc(
    Synopsis(
      Section
        .Raw(Formatter(Formatter.Bold, "Foo"))
    )
  )
  "_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, "Foo")
      )
    )
  )
  "~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikethrough, "Foo")
      )
    )
  )
  "`Foo`" ?= Doc(
    Synopsis(Section.Raw(Code.Inline("Foo")))
  )
  "~*Foo*~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~_Foo_~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "_~Foo~_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_*Foo*_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "*_Foo_*" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "*~Foo~*" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~*Foo*~_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(
          Formatter.Italic,
          Formatter(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
        )
      )
    )
  )
  ///////////////////////////////
  ///// Unclosed formatters /////
  ///////////////////////////////
  "_*Foo*" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter.Unclosed(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~*Foo*" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "***Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold),
        Formatter.Unclosed(Formatter.Bold, "Foo")
      )
    )
  )
  "*_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter.Unclosed(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "~_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "___Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic),
        Formatter.Unclosed(Formatter.Italic, "Foo")
      )
    )
  )
  "*~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikethrough),
        Formatter.Unclosed(Formatter.Strikethrough, "Foo")
      )
    )
  )
  "`import foo`" ?= Doc(
    Synopsis(
      Section.Raw(Code.Inline("import foo"))
    )
  )
  ////////////////////
  ///// Segments /////
  ////////////////////
  "!Important" ?= Doc(
    Synopsis(
      Section.Marked(Section.Marked.Important, Section.Header("Important"))
    )
  )
  "?Info" ?= Doc(
    Synopsis(Section.Marked(Section.Marked.Info, Section.Header("Info")))
  )
  ">Example" ?= Doc(
    Synopsis(
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )
  "?Info\n\n!Important" ?= Doc(
    Synopsis(
      Section.Marked(Section.Marked.Info, Section.Header("Info"), Line())
    ),
    Body(
      Section.Marked(Section.Marked.Important, Section.Header("Important"))
    )
  )
  "?Info\n\n!Important\n\n>Example" ?= Doc(
    Synopsis(
      Section.Marked(Section.Marked.Info, Section.Header("Info"), Line())
    ),
    Body(
      Section.Marked(
        Section.Marked.Important,
        Section.Header("Important"),
        Line()
      ),
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )
  /////////////////
  ///// Lists /////
  /////////////////
  "ul:\n  - Foo\n  - Bar" ?= Doc(
    Synopsis(
      Section.Raw(
        "ul:",
        "\n",
        List(2, List.Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Doc(
    Synopsis(
      Section.Raw(
        "ol:",
        "\n",
        List(2, List.Ordered, " Foo", " Bar")
      )
    )
  )
  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "List",
        "\n",
        List(
          2,
          List.Unordered,
          " First unordered item",
          " Second unordered item",
          List(
            4,
            List.Ordered,
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
    |  - Fourth unordered item""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "List",
        "\n",
        List(
          2,
          List.Unordered,
          " First unordered item",
          " Second unordered item",
          List(
            4,
            List.Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          List(
            4,
            List.Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            List(
              6,
              List.Unordered,
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
    |  - Fourth unordered item""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "List",
        "\n",
        List(
          2,
          List.Unordered,
          " First unordered item",
          " Second unordered item",
          List(
            4,
            List.Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          List(
            4,
            List.Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            List(
              6,
              List.Unordered,
              " First unordered sub item",
              " Second unordered sub item"
            ),
            " Third ordered sub item",
            List.Indent.Invalid(3, " Wrong Indent Item", List.Ordered)
          ),
          " Fourth unordered item"
        )
      )
    )
  )
  /////////////////
  ///// Links /////
  /////////////////
  "[Hello](Http://Google.com)" ?= Doc(
    Synopsis(
      Section.Raw(
        Link.URL(
          "Hello",
          "Http://Google.com"
        )
      )
    )
  )
  "![Media](http://foo.com)" ?= Doc(
    Synopsis(
      Section.Raw(
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
  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?= Doc(
    Synopsis(
      Section.Raw(
        "Foo ",
        Line(
          Formatter(Formatter.Bold, "Foo"),
          " ",
          Formatter(
            Formatter.Strikethrough,
            Formatter.Unclosed(Formatter.Bold, "Bar")
          ),
          " ",
          Code.Inline("foo bar baz bo"),
          " "
        )
      )
    ),
    Body(
      Section.Raw(Section.Header(Line("Hello Section"))),
      Section
        .Marked(
          Section.Marked.Important,
          Section.Header("important"),
          Line()
        ),
      Section.Marked(Section.Marked.Info, Section.Header("Hi"), Line()),
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )
  ////////////////
  ///// Tags /////
  ////////////////
  "DEPRECATED\n" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated))
  )
  "DEPRECATED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated)),
    Synopsis(Section.Raw("Foo"))
  )
  "MODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section.Raw("Foo"))
  )
  "ADDED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Added)),
    Synopsis(Section.Raw("Foo"))
  )
  "REMOVED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Removed)),
    Synopsis(Section.Raw("Foo"))
  )
  "DEPRECATED in 1.0\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0")),
    Synopsis(Section.Raw("Foo"))
  )
  "DEPRECATED in 1.0\nMODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0"), Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section.Raw("Foo"))
  )
}
