package org.enso.syntax.text

import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.ast.Doc._
import org.enso.syntax.text.ast.Doc.Elem._
import org.enso.Logger
import org.enso.flexer.Parser.Result
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Assertion

class DocParserSpec extends FlatSpec with Matchers {
  val logger = new Logger()

  def assertExpr(input: String, result: Doc): Assertion = {
    val output = DocParser.run(input)
    output match {
      case Result(_, Result.Success(value)) =>
        logger.log("\nresult :")
        pprint.pprintln(result, width = 50, height = 10000)
        logger.log("\nvalue :")
        pprint.pprintln(value, width = 50, height = 10000)
        logger.log(s"\ninput  : '$input'")
        logger.log(s"\nv.show : '${value.show()}'\n")
        assert(value == result)
        assert(value.show() == input)
      case _ => fail(s"Parsing failed, consumed ${output.offset} chars")
    }
  }

  implicit class TestString(input: String) {
    def parseTitle(str: String): String = {
      val escape = (str: String) => str.replace("\n", "\\n")
      s"parse `${escape(str)}`"
    }

    private val testBase = it should parseTitle(input)

    def ?=(out: Doc): Unit = testBase in {
      assertExpr(input, out)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //////                  Tests of correct constructions                   /////
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  ////// Formatters ////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "*Foo*" ?= Doc(Synopsis(Section.Raw(Formatter(Formatter.Bold, "Foo"))))
  "_Foo_" ?= Doc(Synopsis(Section.Raw(Formatter(Formatter.Italic, "Foo"))))
  "~Foo~" ?= Doc(
    Synopsis(Section.Raw(Formatter(Formatter.Strikeout, "Foo")))
  )
  "`Foo`" ?= Doc(Synopsis(Section.Raw(Code.Inline("Foo"))))
  "~*Foo*~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
      )
    )
  )
  "~_Foo_~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout, Formatter(Formatter.Italic, "Foo"))
      )
    )
  )
  "_~Foo~_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Italic, Formatter(Formatter.Strikeout, "Foo"))
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
        Formatter(Formatter.Bold, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "_~*Foo*~_" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(
          Formatter.Italic,
          Formatter(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
        )
      )
    )
  )
  "`import foo`" ?= Doc(
    Synopsis(
      Section.Raw(Code.Inline("import foo"))
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  ////// Segments //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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
      Section.Marked(Section.Marked.Info, Section.Header("Info"), Newline)
    ),
    Body(
      Section.Marked(Section.Marked.Important, Section.Header("Important"))
    )
  )
  "?Info\n\n!Important\n\n>Example" ?= Doc(
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

  //////////////////////////////////////////////////////////////////////////////
  ////// Lists /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "ul:\n  - Foo\n  - Bar" ?= Doc(
    Synopsis(
      Section.Raw(
        "ul:",
        Newline,
        List(2, List.Unordered, " Foo", " Bar")
      )
    )
  )
  "ol:\n  * Foo\n  * Bar" ?= Doc(
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
    |  - Third unordered item""".stripMargin ?= Doc(
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
  ////// Links /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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

  //////////////////////////////////////////////////////////////////////////////
  ////// Tags //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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

  //////////////////////////////////////////////////////////////////////////////
  //////                Tests of incorrect constructions                   /////
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  ////// Unclosed formatters ///////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

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
          .Unclosed(Formatter.Strikeout, Formatter(Formatter.Bold, "Foo"))
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
          .Unclosed(Formatter.Strikeout, Formatter(Formatter.Italic, "Foo"))
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
          .Unclosed(Formatter.Bold, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "_~Foo~" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter
          .Unclosed(Formatter.Italic, Formatter(Formatter.Strikeout, "Foo"))
      )
    )
  )
  "~~~Foo" ?= Doc(
    Synopsis(
      Section.Raw(
        Formatter(Formatter.Strikeout),
        Formatter.Unclosed(Formatter.Strikeout, "Foo")
      )
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  ////// Wrong indent //////////////////////////////////////////////////////////
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
    |  - Fourth unordered item""".stripMargin ?= Doc(
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
  ////// other strange constructions ///////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  "Foo *Foo* ~*Bar~ `foo bar baz bo` \n\n\nHello Section\n\n!important\n\n?Hi\n\n>Example" ?= Doc(
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
        Code.Inline("foo bar baz bo"),
        " ",
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
      Section.Marked(Section.Marked.Info, Section.Header("Hi"), Newline),
      Section.Marked(Section.Marked.Example, Section.Header("Example"))
    )
  )

  """   ALAMAKOTA a kot ma ale
    | foo bar""".stripMargin ?= Doc(
    Tags(Tags.Tag(3, Tags.Tag.Unrecognized, "ALAMAKOTA a kot ma ale")),
    Synopsis(Section.Raw(1, "foo bar"))
  )

  """
    | - bar
    | baz
    |""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        Newline,
        " - bar",
        Newline,
        Code(Code.Line(1, "baz")),
        Newline
      )
    )
  )

  " foo *bar* _baz *bo*_" ?= Doc(
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
    |*""".stripMargin ?= Doc(
    Synopsis(Section.Raw("foo ", Formatter(Formatter.Bold, "bar", Newline)))
  )

  """foo _foo
    |_foo2""".stripMargin ?= Doc(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Italic, "foo", Newline), "foo2")
    )
  )

  """foo *foo
    |*foo2""".stripMargin ?= Doc(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Bold, "foo", Newline), "foo2")
    )
  )

  """foo ~foo
    |~foo2""".stripMargin ?= Doc(
    Synopsis(
      Section
        .Raw("foo ", Formatter(Formatter.Strikeout, "foo", Newline), "foo2")
    )
  )

  """   DEPRECATED das sfa asf
    |REMOVED fdsdf
    |Construct and manage a graphical, event-driven user interface for your iOS or
    |tvOS app.
    |
    | foo *foo*""".stripMargin ?= Doc(
    Tags(
      Tags.Tag(3, Tags.Tag.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Removed, " fdsdf")
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
    | foo *foo""".stripMargin ?= Doc(
    Tags(
      Tags.Tag(3, Tags.Tag.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Removed, " fdsdf")
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

  """   DEPRECATED das sfa asf
    |REMOVED fdsdf
    |Construct and manage a graphical, event-driven user interface for your iOS or
    |tvOS app.
    |
    |   fooo bar baz
    |   dsadasfsaf asfasfas
    |   asfasfa sf
    |   asfas fasf """.stripMargin ?= Doc(
    Tags(
      Tags.Tag(3, Tags.Tag.Deprecated, " das sfa asf"),
      Tags.Tag(0, Tags.Tag.Removed, " fdsdf")
    ),
    Synopsis(
      Section.Raw(
        "Construct and manage a graphical, event-driven user interface for your iOS or",
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

  """    DEPRECATED das sfa asf
    |  REMOVED
    | Foo""".stripMargin ?= Doc(
    Tags(
      Tags.Tag(4, Tags.Tag.Deprecated, " das sfa asf"),
      Tags.Tag(2, Tags.Tag.Removed)
    ),
    Synopsis(Section.Raw(1, "Foo"))
  )

  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   siafjaifhjiasjf
    |   fasfknfanfijnf""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        Code(Code.Line(3, "siafjaifhjiasjf"), Code.Line(3, "fasfknfanfijnf"))
      )
    )
  )
  """afsfasfsfjanfjanfa
    |jfnajnfjadnbfjabnf
    |   siafjaifhjiasjf
    |     fasfknfanfijnf
    |   fasfknfanfijnf""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        Code(
          Code.Line(3, "siafjaifhjiasjf"),
          Code.Line(5, "fasfknfanfijnf"),
          Code.Line(3, "fasfknfanfijnf")
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
    |   fasfknfanfijnf""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        Code(
          Code.Line(3, "fasfknfanfijnf"),
          Code.Line(5, "fasfknfanfijnf"),
          Code.Line(10, "fasfknfanfijnf"),
          Code.Line(5, "fasfknfanfijnf"),
          Code.Line(3, "fasfknfanfijnf")
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
    |   fasfknfanfijnf""".stripMargin ?= Doc(
    Synopsis(
      Section.Raw(
        "afsfasfsfjanfjanfa",
        Newline,
        "jfnajnfjadnbfjabnf",
        Newline,
        Code(
          Code.Line(3, "fasfknfanfijnf"),
          Code.Line(5, "fasfknfanfijnf"),
          Code.Line(2, "fasfknfanfijnf"),
          Code.Line(5, "fasfknfanfijnf"),
          Code.Line(3, "fasfknfanfijnf")
        )
      )
    )
  )
  "" ?= Doc()
}
