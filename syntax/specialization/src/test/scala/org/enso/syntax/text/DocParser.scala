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
    Synopsis(Section(0, Section.Raw, Formatter(Formatter.Bold, "Foo")))
  )
  "_Foo_" ?= Doc(
    Synopsis(Section(0, Section.Raw, Formatter(Formatter.Italic, "Foo")))
  )
  "~Foo~" ?= Doc(
    Synopsis(Section(0, Section.Raw, Formatter(Formatter.Strikethrough, "Foo")))
  )
  "`Foo`" ?= Doc(Synopsis(Section(0, Section.Raw, InlineCode("Foo"))))
  "~*Foo*~" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~_Foo_~" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "_~Foo~_" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_*Foo*_" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "*_Foo_*" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "*~Foo~*" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~*Foo*~_" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
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
      Section(
        0,
        Section.Raw,
        Formatter.Unclosed(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~*Foo*" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "***Foo" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Bold),
        Formatter.Unclosed(Formatter.Bold, "Foo")
      )
    )
  )
  "*_Foo_" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter.Unclosed(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "~_Foo_" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter
          .Unclosed(Formatter.Strikethrough, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "___Foo" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Italic),
        Formatter.Unclosed(Formatter.Italic, "Foo")
      )
    )
  )
  "*~Foo~" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter
          .Unclosed(Formatter.Bold, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter
          .Unclosed(Formatter.Italic, Formatter(Formatter.Strikethrough, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        Formatter(Formatter.Strikethrough),
        Formatter.Unclosed(Formatter.Strikethrough, "Foo")
      )
    )
  )
  "`import foo`" ?= Doc(
    Synopsis(Section(0, Section.Raw, InlineCode("import foo")))
  )
  ////////////////////
  ///// Segments /////
  ////////////////////
  "!Important" ?= Doc(
    Synopsis(Section(0, Section.Important, "Important"))
  )
  "?Info"    ?= Doc(Synopsis(Section(0, Section.Info, "Info")))
  ">Example" ?= Doc(Synopsis(Section(0, Section.Example, "Example")))
  "?Info\n\n!Important" ?= Doc(
    Synopsis(Section(0, Section.Info, "Info", "\n")),
    Body(Section(0, Section.Important, "Important"))
  )
  "?Info\n\n!Important\n\n>Example" ?= Doc(
    Synopsis(
      Section(0, Section.Info, "Info", "\n")
    ),
    Body(
      Section(0, Section.Important, "Important", "\n"),
      Section(0, Section.Example, "Example")
    )
  )
  /////////////////
  ///// Lists /////
  /////////////////
  "ul:\n  - Foo\n  - Bar" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        "ul:",
        "\n",
        ListBlock(2, ListBlock.Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
        "ol:",
        "\n",
        ListBlock(2, ListBlock.Ordered, " Foo", " Bar")
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
      Section(
        0,
        Section.Raw,
        "List",
        "\n",
        ListBlock(
          2,
          ListBlock.Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            ListBlock.Ordered,
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
      Section(
        0,
        Section.Raw,
        "List",
        "\n",
        ListBlock(
          2,
          ListBlock.Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            ListBlock.Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          ListBlock(
            4,
            ListBlock.Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            ListBlock(
              6,
              ListBlock.Unordered,
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
      Section(
        0,
        Section.Raw,
        "List",
        "\n",
        ListBlock(
          2,
          ListBlock.Unordered,
          " First unordered item",
          " Second unordered item",
          ListBlock(
            4,
            ListBlock.Ordered,
            " First ordered sub item",
            " Second ordered sub item"
          ),
          " Third unordered item",
          ListBlock(
            4,
            ListBlock.Ordered,
            " First ordered sub item",
            " Second ordered sub item",
            ListBlock(
              6,
              ListBlock.Unordered,
              " First unordered sub item",
              " Second unordered sub item"
            ),
            " Third ordered sub item",
            ListBlock.Indent.Invalid(3, " Wrong Indent Item", ListBlock.Ordered)
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
      Section(
        0,
        Section.Raw,
        Link.URL(
          "Hello",
          "Http://Google.com"
        )
      )
    )
  )
  "![Media](http://foo.com)" ?= Doc(
    Synopsis(
      Section(
        0,
        Section.Raw,
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
      Section(
        0,
        Section.Raw,
        "Foo ",
        Formatter(Formatter.Bold, "Foo"),
        " ",
        Formatter(
          Formatter.Strikethrough,
          Formatter.Unclosed(Formatter.Bold, "Bar")
        ),
        " ",
        InlineCode("foo bar baz bo"),
        " ",
        "\n"
      )
    ),
    Body(
      Section(0, Section.Raw, "Hello Section", "\n"),
      Section(0, Section.Important, "important", "\n"),
      Section(0, Section.Info, "Hi", "\n"),
      Section(0, Section.Example, "Example")
    )
  )
  ////////////////
  ///// Tags /////
  ////////////////
  "DEPRECATED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated)),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
  "MODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
  "ADDED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Added)),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
  "REMOVED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Removed)),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
  "DEPRECATED in 1.0\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0")),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
  "DEPRECATED in 1.0\nMODIFIED\nFoo" ?= Doc(
    Tags(Tags.Tag(Tags.Tag.Deprecated, " in 1.0"), Tags.Tag(Tags.Tag.Modified)),
    Synopsis(Section(0, Section.Raw, "Foo"))
  )
}
