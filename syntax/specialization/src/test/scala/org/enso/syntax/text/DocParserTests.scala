package org.enso.syntax.text

import org.enso.syntax.text.ast.Documented
import org.enso.syntax.text.ast.Documented._
import org.enso.syntax.text.ast.Documented.Elem._
import org.enso.Logger
import org.enso.flexer.Parser.Result
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Assertion

class DocParserTests extends FlatSpec with Matchers {
  val logger = new Logger()

  def assertExpr(input: String, result: Documented): Assertion = {
    val output = DocParser.run(input)
    output match {
      case Result(_, Result.Success(value)) =>
        assert(value == result)
        assert(value.show() == input)
      case _ =>
        fail(s"Parsing documentation failed, consumed ${output.offset} chars")
    }
  }

  implicit class TestString(input: String) {
    def parseDocumentation(str: String): String = {
      val escape = (str: String) => str.replace("\n", "\\n")
      s"parse `${escape(str)}`"
    }

    private val testBase = it should parseDocumentation(input)

    def ?=(out: Documented): Unit = testBase in {
      assertExpr(input, out)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////                  Documentation Parser Test Suite                    /////
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  //// Formatters //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "*Foo*" ?= Documented(
    Synopsis(Section.Raw(Formatter(Formatter.Bold, "Foo")))
  )
  "_Foo_" ?= Documented(
    Synopsis(Section.Raw(Formatter(Formatter.Italic, "Foo")))
  )
  "~Foo~" ?= Documented(
    Synopsis(Section.Raw(Formatter(Formatter.Strikeout, "Foo")))
  )
  "`Foo`" ?= Documented(Synopsis(Section.Raw(CodeBlock.Inline("Foo"))))
  "~*Foo*~" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~_Foo_~" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "_~Foo~_" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "_*Foo*_" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "*_Foo_*" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "*~Foo~*" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "_~*Foo*~_" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(
          Formatter.Italic,
          Formatter(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
        )
      )
    )
  )
  "`import foo`" ?= Documented(
    Synopsis(
      Section.Raw(CodeBlock.Inline("import foo"))
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Unclosed formatters /////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "_*Foo*" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter.Unclosed(Formatter.Italic, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~*Foo*" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "***Foo" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Bold),
        Formatter.Unclosed(Formatter.Bold, "Foo")
      )
    )
  )
  "*_Foo_" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter.Unclosed(Formatter.Bold, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "~_Foo_" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Strikeout, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "___Foo" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic),
        Formatter.Unclosed(Formatter.Italic, "Foo")
      )
    )
  )
  "*~Foo~" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Bold, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Italic, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Documented(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout),
        Formatter.Unclosed(Formatter.Strikeout, "Foo")
      )
    )
  )
  " foo *bar* _baz *bo*_" ?= Documented(
    Synopsis(
      Section.Raw(
        1,
        "foo ",
        Formatter(Formatter.Bold, "bar"),
        " ",
        Formatter(Formatter.Italic, "baz ", Formatter(Formatter.Bold, "bo"))
      )
    )
  )
  """foo *bar
    |*""".stripMargin ?= Documented(
    Synopsis(Section.Raw("foo ", Formatter(Formatter.Bold, "bar", Newline)))
  )

  """foo _foo
    |_foo2""".stripMargin ?= Documented(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Italic, "foo", Newline), "foo2")
    )
  )

  """foo *foo
    |*foo2""".stripMargin ?= Documented(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Bold, "foo", Newline), "foo2")
    )
  )

  """foo ~foo
    |~foo2""".stripMargin ?= Documented(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Strikeout, "foo", Newline), "foo2")
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Segments ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "!Important" ?= Documented(
    Synopsis(
      Section.Marked(Section.Marked.Important, Section.Header("Important"))
    )
  )
  " ! Important" ?= Documented(
    Synopsis(
      Section
        .Marked(1, 1, Section.Marked.Important, Section.Header("Important"))
    )
  )
  "   ! Important" ?= Documented(
    Synopsis(
      Section
        .Marked(3, 1, Section.Marked.Important, Section.Header("Important"))
    )
  )
  " !    Important" ?= Documented(
    Synopsis(
      Section
        .Marked(1, 4, Section.Marked.Important, Section.Header("Important"))
    )
  )
  "?Info" ?= Documented(
    Synopsis(Section.Marked(Section.Marked.Info, Section.Header("Info")))
  )
  ">Example" ?= Documented(
    Synopsis(
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )
  """?Info
    |
    |!Important""".stripMargin ?= Documented(
    Synopsis(
      Section.Marked(Section.Marked.Info, Section.Header("Info"), Newline)
    ),
    Body(
      Section.Marked(Section.Marked.Important, Section.Header("Important"))
    )
  )
  """?Info
    |
    |!Important
    |
    |>Example""".stripMargin ?= Documented(
    Synopsis(
      Section.Marked(Section.Marked.Info, Section.Header("Info"), Newline)
    ),
    Body(
      Section.Marked(
        Section.Marked.Important,
        Section.Header("Important"),
        Newline
      ),
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )
  """Foo *Foo* ~*Bar~ `foo bar baz bo`
    |
    |
    |Hello Section
    |
    |!important
    |
    |?info
    |
    |>Example""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "Foo ",
        Formatter(Formatter.Bold, "Foo"),
        " ",
        Formatter(
          Formatter.Strikeout,
          Formatter.Unclosed(Formatter.Bold, "Bar")
        ),
        " ",
        CodeBlock.Inline("foo bar baz bo"),
        Newline
      )
    ),
    Body(
      Section.Raw(Section.Header("Hello Section"), Newline),
      Section
        .Marked(
          Section.Marked.Important,
          Section.Header("important"),
          Newline
        ),
      Section.Marked(Section.Marked.Info, Section.Header("info"), Newline),
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Lists ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "ul:\n  - Foo\n  - Bar" ?= Documented(
    Synopsis(
      Section.Raw(
        "ul:",
        Newline,
        List(2, List.Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Documented(
    Synopsis(
      Section.Raw(
        "ol:",
        Newline,
        List(2, List.Ordered, " Foo", " Bar")
      )
    )
  )
  """List
    |  - First unordered item
    |  - Second unordered item
    |    * First ordered sub item
    |    * Second ordered sub item
    |  - Third unordered item""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "List",
        Newline,
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
    |    *    Second ordered sub item
    |  - Third unordered item""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "List",
        Newline,
        List(
          2,
          List.Unordered,
          " First unordered item",
          " Second unordered item",
          List(
            4,
            List.Ordered,
            " First ordered sub item",
            "    Second ordered sub item"
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
    |  - Fourth unordered item""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "List",
        Newline,
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

  //////////////////////////////////////////////////////////////////////////////
  //// Wrong indent ////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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
    |  - Fourth unordered item""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "List",
        Newline,
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
            List.Indent.Invalid(3, List.Ordered, " Wrong Indent Item")
          ),
          " Fourth unordered item"
        )
      )
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Links ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "[Hello](Http://Google.com)" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.URL(
          "Hello",
          "Http://Google.com"
        )
      )
    )
  )
  "![Media](http://foo.com)" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Image(
          "Media",
          "http://foo.com"
        )
      )
    )
  )
  "![foo)" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "![foo)"
        )
      )
    )
  )
  "[foo)" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "[foo)"
        )
      )
    )
  )
  "[foo]bo)" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "[foo]bo)"
        )
      )
    )
  )
  "![foo]google" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "![foo]google"
        )
      )
    )
  )

  "[foo]google" ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "[foo]google"
        )
      )
    )
  )

  """[foo]bo)
    |basdbasd""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        Link.Invalid(
          "[foo]bo)"
        ),
        Newline,
        "basdbasd"
      )
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Tags ////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val allPossibleTags = Tags.Tag.Type.codes.-(Tags.Tag.Unrecognized)

  allPossibleTags.foreach(
    t =>
      s"${t.toString.toUpperCase()}\nFoo" ?= Documented(
        Tags(Tags.Tag(t)),
        Synopsis(Section.Raw("Foo"))
      )
  )
  "DEPRECATED in 1.0\nFoo" ?= Documented(
    Tags(Tags.Tag(Tags.Tag.Type.Deprecated, " in 1.0")),
    Synopsis(Section.Raw("Foo"))
  )
  "DEPRECATED in 1.0\nMODIFIED\nFoo" ?= Documented(
    Tags(
      Tags.Tag(Tags.Tag.Type.Deprecated, " in 1.0"),
      Tags.Tag(Tags.Tag.Type.Modified)
    ),
    Synopsis(Section.Raw("Foo"))
  )
  """   ALAMAKOTA a kot ma ale
    | foo bar""".stripMargin ?= Documented(
    Tags(Tags.Tag(3, Tags.Tag.Unrecognized, "ALAMAKOTA a kot ma ale")),
    Synopsis(Section.Raw(1, "foo bar"))
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Multiline code //////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   siafjaifhjiasjf
    |   fasfknfanfijnf""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        CodeBlock(
          CodeBlock.Line(3, "siafjaifhjiasjf"),
          CodeBlock.Line(3, "fasfknfanfijnf")
        )
      )
    )
  )
  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   siafjaifhjiasjf
    |     fasfknfanfijnf
    |   fasfknfanfijnf""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        CodeBlock(
          CodeBlock.Line(3, "siafjaifhjiasjf"),
          CodeBlock.Line(5, "fasfknfanfijnf"),
          CodeBlock.Line(3, "fasfknfanfijnf")
        )
      )
    )
  )
  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   fasfknfanfijnf
    |     fasfknfanfijnf
    |          fasfknfanfijnf
    |     fasfknfanfijnf
    |   fasfknfanfijnf""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        CodeBlock(
          CodeBlock.Line(3, "fasfknfanfijnf"),
          CodeBlock.Line(5, "fasfknfanfijnf"),
          CodeBlock.Line(10, "fasfknfanfijnf"),
          CodeBlock.Line(5, "fasfknfanfijnf"),
          CodeBlock.Line(3, "fasfknfanfijnf")
        )
      )
    )
  )
  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   fasfknfanfijnf
    |     fasfknfanfijnf
    |  fasfknfanfijnf
    |     fasfknfanfijnf
    |   fasfknfanfijnf""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        CodeBlock(
          CodeBlock.Line(3, "fasfknfanfijnf"),
          CodeBlock.Line(5, "fasfknfanfijnf"),
          CodeBlock.Line(2, "fasfknfanfijnf"),
          CodeBlock.Line(5, "fasfknfanfijnf"),
          CodeBlock.Line(3, "fasfknfanfijnf")
        )
      )
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  //// Unclassified tests //////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  """
    | - bar
    | baz
    |""".stripMargin ?= Documented(
    Synopsis(
      Section.Raw(
        Newline,
        " - bar",
        Newline,
        CodeBlock(CodeBlock.Line(1, "baz")),
        Newline
      )
    )
  )

  """   DEPRECATED das sfa asf
    |REMOVED fdsdf
    |Construct and manage a graphical, event-driven user interface for your iOS or
    |tvOS app.
    |
    | foo *foo*""".stripMargin ?= Documented(
    Tags(
      Tags.Tag(3, Tags.Tag.Type.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Type.Removed, " fdsdf")
    ),
    Synopsis(
      Section.Raw(
        "Construct and manage a graphical, event-driven user interface for your iOS or",
        Newline,
        "tvOS app.",
        Newline
      )
    ),
    Body(Section.Raw(1, "foo ", Formatter(Formatter.Bold, "foo")))
  )

  """   DEPRECATED das sfa asf
    |REMOVED fdsdf
    |Construct and manage a graphical, event-driven user interface for your iOS or
    |tvOS app.
    |
    | foo *foo""".stripMargin ?= Documented(
    Tags(
      Tags.Tag(3, Tags.Tag.Type.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Type.Removed, " fdsdf")
    ),
    Synopsis(
      Section.Raw(
        "Construct and manage a graphical, event-driven user interface for your iOS or",
        Newline,
        "tvOS app.",
        Newline
      )
    ),
    Body(Section.Raw(1, "foo ", Formatter.Unclosed(Formatter.Bold, "foo")))
  )

  """    DEPRECATED das sfa asf
    |  REMOVED
    | Foo""".stripMargin ?= Documented(
    Tags(
      Tags.Tag(4, Tags.Tag.Type.Deprecated, " das sfa asf"),
      Tags.Tag(2, Tags.Tag.Type.Removed)
    ),
    Synopsis(Section.Raw(1, "Foo"))
  )

  """   DEPRECATED das sfa asf
    |REMOVED fdsdf
    |Construct and manage a graphical user interface for your iOS or
    |tvOS app.
    |
    |   fooo bar baz
    |   dsadasfsaf asfasfas
    |   asfasfa sf
    |   asfas fasf """.stripMargin ?= Documented(
    Tags(
      Tags.Tag(3, Tags.Tag.Type.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Type.Removed, " fdsdf")
    ),
    Synopsis(
      Section.Raw(
        "Construct and manage a graphical user interface for your iOS or",
        Newline,
        "tvOS app.",
        Newline
      )
    ),
    Body(
      Section.Raw(
        3,
        "fooo bar baz",
        Newline,
        "dsadasfsaf asfasfas",
        Newline,
        "asfasfa sf",
        Newline,
        "asfas fasf "
      )
    )
  )
}
