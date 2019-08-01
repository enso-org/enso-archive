package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.data.List1
import org.enso.syntax.text.ast.Repr

import scalatags.{Text => HTMLBundle}
import HTMLBundle._
import HTMLBundle.{all => HTML}
import HTML._

object DocAST {
  type HTML    = Seq[Modifier]
  type HTMLTag = TypedTag[String]

  trait Indentable {
    def makeIndent(size: Int): String = " " * size
  }

  //////////////
  /// Symbol ///
  //////////////

  trait Symbol extends AST.Symbol {
    val html: HTML

    val docMeta: HTMLTag =
      HTML.meta(HTML.httpEquiv := "Content-Type")(HTML.content := "text/html")(
        HTML.charset := "UTF-8"
      )
    val cssLink: HTMLTag =
      HTML.link(HTML.rel := "stylesheet")(HTML.href := "style.css")

    def renderHTML(): HTMLTag =
      scalatags.Text.all
        .html(HTML.head(docMeta, cssLink), HTML.body(html))
  }

  implicit final class _OptionAST_(val self: Option[AST]) extends Symbol {
    val repr: Repr = self.map(_.repr).getOrElse(Repr())
    val html: HTML = self.map(_.html).getOrElse("".html)
  }

  ///////////
  /// AST ///
  ///////////

  trait AST        extends Symbol
  trait InvalidAST extends AST

  final case class Text(text: String) extends AST {
    val repr: Repr = text
    val html: HTML = Seq(text)
  }

  implicit def stringToText(str: String): Text = Text(str)

  //////////////////////
  /// Text Formatter ///
  //////////////////////

  final case class Formatter(tp: Formatter.Type, elem: Option[AST])
      extends AST {
    val repr: Repr = Repr(tp.marker) + elem.repr + tp.marker
    val html: HTML = Seq(tp.htmlMarker(elem.html))
  }
  object Formatter {
    def apply(tp: Type): Formatter = Formatter(tp, None)
    def apply(tp: Type, elem: AST): Formatter =
      Formatter(tp, Some(elem))

    abstract class Type(
      val marker: Char,
      val htmlMarker: HTMLTag
    )
    case object Bold          extends Type('*', HTML.b)
    case object Italic        extends Type('_', HTML.i)
    case object Strikethrough extends Type('~', HTML.s)

    final case class Unclosed(tp: Type, elem: Option[AST]) extends InvalidAST {
      val repr: Repr = Repr(tp.marker) + elem.repr

      //      TODO:
      //      Generating css classes for the elements is really great idea.
      //      Please think how you can generalize it, so for all other types they
      //      will be generated automatically. Of course sometimes, like here,
      //      you would liek to override the automatic generation (unclosed) with
      //      something custom.
      //      Here for example, I'd vote for applying multiple CSS classes
      //      both .bold as well as .unclosed
      //      this way you can write generic CSS rules.

      val html: HTML = Seq {
        val className = s"unclosed_${tp.htmlMarker.tag}"
        HTML.div(HTML.`class` := className)(elem.html)
      }
    }
    object Unclosed {
      def apply(tp: Type): Unclosed = Unclosed(tp, None)
      def apply(tp: Type, elem: AST): Unclosed =
        Unclosed(tp, Option(elem))
    }
  }

  ///////////////////////
  ////// Code Line //////
  ///////////////////////

  final case class InlineCode(str: String) extends AST {
    val repr: Repr = Repr("`") + str + "`"
    val html: HTML = Seq(HTML.code(str))
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  abstract class Link(name: String, url: String, marker: String) extends AST {
    val repr: Repr = Repr() + marker + name + "](" + url + ")"
    val html: HTML = this match {
      case _: Link.URL   => Seq(HTML.a(HTML.href := url)(name))
      case _: Link.Image => Seq(HTML.img(HTML.src := url), name)
    }
  }

  object Link {
    final case class URL(name: String, url: String)
        extends Link(name, url, "[") {
      val marker = "["
    }
    object URL {
      def apply(): URL = new URL("", "")
    }

    final case class Image(name: String, url: String)
        extends Link(name, url, "![") {
      val marker = "!["
    }
    object Image {
      def apply(): Image = new Image("", "")
    }
  }

  ///////////////////
  ////// Lists //////
  ///////////////////

  final case class ListBlock(indent: Int, tp: ListBlock.Type, elems: List1[AST])
      extends AST
      with Indentable {

    val repr: Repr = {
      var _repr = Repr()
      elems.toList.foreach {
        case elem @ (_: InvalidAST) =>
          _repr += '\n'
          _repr += elem.repr
        case elem @ (_: ListBlock) =>
          _repr += '\n'
          _repr += elem.repr
        case elem =>
          if (elems.head != elem) {
            _repr += '\n'
          }
          _repr += makeIndent(indent)
          _repr += tp.marker
          _repr += elem.repr
      }
      _repr
    }

    val html: HTML = Seq(tp.HTMLMarker({
      elems.toList.map({
        case elem @ (_: ListBlock) => elem.html
        case elem                  => Seq(HTML.li(elem.html))
      })
    }))
  }
  object ListBlock {
    def apply(indent: Int, listType: Type, elem: AST): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elem, Nil))
    def apply(indent: Int, listType: Type, elems: AST*): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elems.head, elems.tail.to[List]))

    trait Type {
      val marker: Char
      val HTMLMarker: HTMLTag
    }
    final case object Unordered extends Type {
      val marker              = '-'
      val HTMLMarker: HTMLTag = HTML.ul
    }
    final case object Ordered extends Type {
      val marker              = '*'
      val HTMLMarker: HTMLTag = HTML.ol
    }

    //////////////////////
    /// Invalid Indent ///
    //////////////////////

    final case class InvalidIndent(
      indent: Int,
      elem: AST,
      tp: Type
    ) extends InvalidAST
        with Indentable {
      val repr: Repr = Repr(makeIndent(indent)) + tp.marker + elem.repr
      val html: HTML = Seq(HTML.div(HTML.`class` := "InvalidIndent")(elem.html))
    }
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  case class Section(indent: Int, st: Section.Type, elems: List[AST])
      extends Symbol
      with Indentable {
    val markerEmptyInNull: String    = st.marker.map(_.toString).getOrElse("")
    val markerNonEmptyInNull: String = st.marker.map(_.toString).getOrElse(" ")
    val newline                      = Text("\n")

    val repr: Repr = {
      var _repr = Repr()

      _repr += (indent match {
        case 0 => markerEmptyInNull
        case 1 => markerNonEmptyInNull
        case _ => {
          val indentAfterMarker: String = makeIndent(1)
          makeIndent(
            indent - (markerNonEmptyInNull.length + indentAfterMarker.length)
          ) + markerNonEmptyInNull + indentAfterMarker
        }
      })

      for (currElemPos <- elems.indices) {
        val elem = elems(currElemPos)
        elem match {
          case _: Section.Header =>
            _repr += "\n" + makeIndent(indent)
            _repr += elem.repr
          case _: ListBlock =>
            _repr += elem.repr
          case _ =>
            if (currElemPos > 0) {
              val previousElem = elems(currElemPos - 1)
              if (previousElem == newline) {
                _repr += makeIndent(indent)
              }
            }
            _repr += elem.repr
        }
      }
      _repr
    }
    val html: HTML =
      Seq(HTML.div(HTML.`class` := st.toString)(elems.map(_.html)))

  }

  object Section {
    def apply(indent: Int, st: Type, elem: AST): Section =
      Section(indent, st, elem :: Nil)
    def apply(indent: Int, st: Type, elems: AST*): Section =
      Section(indent, st, elems.to[List])

    final case class Header(elem: AST) extends AST {
      val repr: Repr = elem.repr
      val html: HTML = Seq(HTML.div(HTML.`class` := "Header")(elem.html))
    }

    trait Type {
      val marker: Option[Char]
    }
    case object Important extends Type {
      val marker: Option[Char] = Some('!')
    }
    case object Info extends Type {
      val marker: Option[Char] = Some('?')
    }
    case object Example extends Type {
      val marker: Option[Char] = Some('>')
    }
    case object Code extends Type {
      val marker: Option[Char] = None

      final case class Line(str: String) extends AST {
        val repr: Repr = str
        val html: HTML = Seq(HTML.code(str), HTML.br)
      }
    }
    case object Raw extends Type {
      val marker: Option[Char] = None
    }
  }

  /////////////////////
  ////// Details //////
  /////////////////////

  final case class Details(elems: List[Section]) extends AST {
    val repr: Repr = elems.map(_.repr.show()).mkString("\n")
    val html: HTML = Seq(
      HTML.div(HTML.`class` := this.getClass.getSimpleName)(
        elems.map(_.html)
      )
    )
    def exists(): Boolean = Details(elems) != Details()
  }

  object Details {
    def apply():                Details = Details(Nil)
    def apply(elem: Section):   Details = Details(elem :: Nil)
    def apply(elems: Section*): Details = Details(elems.to[List])
  }

  //////////////////////
  ////// Synopsis //////
  //////////////////////

  final case class Synopsis(elems: List[Section]) extends AST {
    val repr: Repr = elems.map(_.repr.show()).mkString("\n")
    val html: HTML =
      Seq(
        HTML.div(HTML.`class` := this.getClass.getSimpleName)(
          elems.map(_.html)
        )
      )
    def exists(): Boolean = Synopsis(elems) != Synopsis()
  }
  object Synopsis {
    def apply():                Synopsis = Synopsis(Nil)
    def apply(elem: Section):   Synopsis = Synopsis(elem :: Nil)
    def apply(elems: Section*): Synopsis = Synopsis(elems.to[List])
  }

  ////////////
  /// Tags ///
  ////////////

  final case class Tag(tp: Tag.Type, details: Option[String]) extends AST {
    val name: String = tp.toString.toUpperCase
    val repr: Repr = {
      details.repr match {
        case Repr.R => Repr(name)
        case _      => Repr(name) + details.repr
      }
    }
    val html: HTML = Seq(HTML.div(HTML.`class` := name)(name)(details.html))
  }
  object Tag {
    def apply(tp: Type): Tag = Tag(tp, None)
    def apply(tp: Type, details: String): Tag =
      Tag(tp, Option(details))

    sealed trait Type
    case object Deprecated extends Type
    case object Added      extends Type
    case object Removed    extends Type
    case object Modified   extends Type
    case object Upcoming   extends Type
  }

  final case class Tags(indent: Int, elems: List[Tag])
      extends AST
      with Indentable {
    val repr: Repr =
      elems.map(makeIndent(indent) + _.repr.show()).mkString("\n")
    val html: HTML = Seq(
      HTML.div(HTML.`class` := this.getClass.getSimpleName)(
        elems.map(_.html)
      )
    )
    def exists(): Boolean = Tags(indent, elems) != Tags(indent)
  }
  object Tags {
    def apply():                       Tags = Tags(0, Nil)
    def apply(indent: Int):            Tags = Tags(indent, Nil)
    def apply(elem: Tag):              Tags = Tags(0, elem :: Nil)
    def apply(indent: Int, elem: Tag): Tags = Tags(indent, elem :: Nil)
    def apply(elems: Tag*):            Tags = Tags(0, elems.to[List])
    def apply(indent: Int, elems: Tag*): Tags =
      Tags(indent, elems.to[List])
  }

  implicit final class _OptionTagType_(val self: Option[String]) extends AST {
    val repr: Repr = self.map(Repr(_)).getOrElse(Repr())
    val html: HTML =
      Seq(
        self.map(HTML.div(HTML.`class` := "tag_details")(_)).getOrElse("".html)
      )
  }

  ////////////////
  ////// Doc /////
  ////////////////

  final case class Doc(
    tags: Tags,
    synopsis: Synopsis,
    details: Details
  ) extends AST {
    val repr: Repr = {
      val tagsRepr = if (tags.exists()) {
        if (synopsis.exists() || details.exists()) {
          tags.repr + Repr("\n")
        } else {
          tags.repr
        }
      } else Repr()

      val synopsisRepr = if (synopsis.exists()) {
        synopsis.repr
      } else Repr()

      val detailsRepr = if (details.exists()) {
        Repr("\n") + details.repr
      } else Repr()

      Repr() + tagsRepr + synopsisRepr + detailsRepr
    }

    val html: HTML = {
      val tagsHtml     = if (tags.exists()) tags.html else "".html
      val synopsisHtml = if (synopsis.exists()) synopsis.html else "".html
      val detailsHtml  = if (details.exists()) details.html else "".html
      Seq(
        HTML.div(HTML.`class` := this.getClass.getSimpleName)(tagsHtml)(
          synopsisHtml
        )(
          detailsHtml
        )
      )
    }
  }

  object Doc {
    def apply(tags: Tags, synopsis: Synopsis, body: Details): Doc =
      new Doc(tags, synopsis, body)
    def apply(synopsis: Synopsis, body: Details): Doc =
      new Doc(Tags(), synopsis, body)
    def apply(synopsis: Synopsis): Doc =
      new Doc(Tags(), synopsis, Details(Nil))
    def apply(tags: Tags): Doc =
      new Doc(tags, Synopsis(Nil), Details(Nil))
    def apply(tags: Tags, synopsis: Synopsis): Doc =
      new Doc(tags, synopsis, Details(Nil))
  }
}

// TODO
// 1. This should be extracted to top-level and the file should be renamed to org.enso..... .ast.Doc.
// Then the object AST will be named Doc as well , and everything will be so much nicer, like Doc.Tag
// 2. parent object should not inspect children when printing out. (.exists())
// 3. Synopsis/Details/Doc/Tags - this type is wrong. It allows creating synopsis next to bold, or inline code.
// Make sure the types does not allow for cosntructing wong AST!
// Please make sure that all other places are fixed as well. This is the most important design part.
// 4. repetitive code
// 5. -> AST.Invalid
// 6. -> Indent.Invalid
// 7. After thinking for a while about it, I would change the name of AST here to something else.
// AST does not describe well enough such items as InlineCode.
// In fact your AST has only 1 habitant - Doc.
// Everything else are other strucutres which build up the Doc.
// Think about better name here, Moreover
// I believe that InlineCode, Link, etc, should be grouped in a namespace.
