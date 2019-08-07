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
        logger.log(s"\nresult : '$result'")
        logger.log(s"value  : '$value'")
        logger.log(s"\ninput  : '$input'")
        logger.log(s"v.show : '${value.show()}'\n")
        logger.log(s"\nv.html : '${value.renderHTML()}'")
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
        .Raw(0, Formatter(Formatter.Bold, "Foo"))
    )
  )
  "_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Italic, "Foo")
      )
    )
  )
  "~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Strikethrough, "Foo")
      )
    )
  )
  "`Foo`" ?= Doc(
    Synopsis(Section.Raw(0, Code.Inline("Foo")))
  )
  "~*Foo*~" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~_Foo_~" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "_~Foo~_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_*Foo*_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "*_Foo_*" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "*~Foo~*" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~*Foo*~_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
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
        0,
        Formatter.Unclosed(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~*Foo*" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "***Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Bold),
        Formatter.Unclosed(Formatter.Bold, "Foo")
      )
    )
  )
  "*_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter.Unclosed(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "~_Foo_" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "___Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Italic),
        Formatter.Unclosed(Formatter.Italic, "Foo")
      )
    )
  )
  "*~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter
          .Unclosed(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter
          .Unclosed(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        Formatter(Formatter.Strikethrough),
        Formatter.Unclosed(Formatter.Strikethrough, "Foo")
      )
    )
  )
  "`import foo`" ?= Doc(
    Synopsis(
      Section.Raw(0, Code.Inline("import foo"))
    )
  )
  ////////////////////
  ///// Segments /////
  ////////////////////
  "!Important" ?= Doc(
    Synopsis(Section.Marked(0, Section.Marked.Important, "Important"))
  )
  "?Info" ?= Doc(Synopsis(Section.Marked(0, Section.Marked.Info, "Info")))
  ">Example" ?= Doc(
    Synopsis(Section.Marked(0, Section.Marked.Example, "Example"))
  )
  "?Info\n\n!Important" ?= Doc(
    Synopsis(Section.Marked(0, Section.Marked.Info, "Info", "\n")),
    Body(Section.Marked(0, Section.Marked.Important, "Important"))
  )
  "?Info\n\n!Important\n\n>Example" ?= Doc(
    Synopsis(
      Section.Marked(0, Section.Marked.Info, "Info", "\n")
    ),
    Body(
      Section.Marked(0, Section.Marked.Important, "Important", "\n"),
      Section.Marked(0, Section.Marked.Example, "Example")
    )
  )
  /////////////////
  ///// Lists /////
  /////////////////
  "ul:\n  - Foo\n  - Bar" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        "ul:",
        "\n",
        List(2, List.Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
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
        0,
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
        0,
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
        0,
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
        0,
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
        0,
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
  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?= Doc(
    Synopsis(
      Section.Raw(
        0,
        "Foo ",
        Formatter(Formatter.Bold, "Foo"),
        " ",
        Formatter(
          Formatter.Strikethrough,
          Formatter.Unclosed(Formatter.Bold, "Bar")
        ),
        " ",
        Code.Inline("foo bar baz bo"),
        " ",
        "\n"
      )
    ),
    Body(
      Section.Raw(0, "Hello Section", "\n"),
      Section.Marked(0, Section.Marked.Important, "important", "\n"),
      Section.Marked(0, Section.Marked.Info, "Hi", "\n"),
      Section.Marked(0, Section.Marked.Example, "Example")
    )
  )
  ////////////////
  ///// Tags /////
  ////////////////
  "DEPRECATED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated)),
    Synopsis(Section.Raw(0, "Foo"))
  )
  "MODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section.Raw(0, "Foo"))
  )
  "ADDED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Added)),
    Synopsis(Section.Raw(0, "Foo"))
  )
  "REMOVED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Removed)),
    Synopsis(Section.Raw(0, "Foo"))
  )
  "DEPRECATED in 1.0\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0")),
    Synopsis(Section.Raw(0, "Foo"))
  )
  "DEPRECATED in 1.0\nMODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0"), Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section.Raw(0, "Foo"))
  )
}
