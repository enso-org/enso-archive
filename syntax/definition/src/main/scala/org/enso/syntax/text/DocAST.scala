package org.enso.syntax.text

import cats.data.NonEmptyList
import org.enso.data.List1
import org.enso.syntax.text.ast.Repr
import scalatags.Text._
import scalatags.Text.all._

object DocAST {

  //////////////
  /// Symbol ///
  //////////////

  trait Symbol extends AST.Symbol {
    type HTML    = Seq[Modifier]
    type HTMLTag = TypedTag[String]
    val html: HTML

    val docMeta: HTMLTag =
      meta(httpEquiv := "Content-Type")(content := "text/html")(
        charset := "UTF-8"
      )
    val cssLink: HTMLTag = link(rel := "stylesheet")(href := "style.css")

    def renderHTML(): HTMLTag =
      scalatags.Text.all.html(head(docMeta, cssLink), body(html))

    def makeIndent(size: Int) = " " * size
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
      val htmlMarker: ConcreteHtmlTag[String]
    )
    case object Bold          extends Type('*', b)
    case object Italic        extends Type('_', i)
    case object Strikethrough extends Type('~', s)

    final case class Unclosed(tp: Type, elem: Option[AST]) extends InvalidAST {
      val repr: Repr = Repr(tp.marker) + elem.repr
      val html: HTML = {
        val className = s"unclosed_${tp.htmlMarker.tag}"
        Seq(div(`class` := className)(elem.html))
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
    val html: HTML = Seq(code(str))
  }

  ///////////////////
  ////// Links //////
  ///////////////////

  abstract class Link(name: String, url: String, marker: String) extends AST {
    val repr: Repr = Repr() + marker + name + "](" + url + ")"
    val html: HTML = this match {
      case _: Link.URL   => Seq(a(href := url)(name))
      case _: Link.Image => Seq(img(src := url), name)
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
      extends AST {

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

    val html: HTML = Seq(
      tp.HTMLMarker({
        for (elem <- elems.toList) yield {
          elem match {
            case _: ListBlock => elem.html
            case _            => Seq(li(elem.html))
          }
        }
      })
    )
  }
  object ListBlock {
    def apply(indent: Int, listType: Type, elem: AST): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elem, Nil))
    def apply(indent: Int, listType: Type, elems: AST*): ListBlock =
      ListBlock(indent, listType, NonEmptyList(elems.head, elems.tail.to[List]))

    trait Type {
      val marker: Char
      val HTMLMarker: ConcreteHtmlTag[String]
    }
    final case object Unordered extends Type {
      val marker     = '-'
      val HTMLMarker = ul
    }
    final case object Ordered extends Type {
      val marker     = '*'
      val HTMLMarker = ol
    }

    //////////////////////
    /// Invalid Indent ///
    //////////////////////

    final case class InvalidIndent(
      indent: Int,
      elem: AST,
      tp: Type
    ) extends InvalidAST {
      val repr: Repr = Repr(makeIndent(indent)) + tp.marker + elem.repr
      val html: HTML =
        Seq(div(`class` := "InvalidIndent")(elem.html))
    }
  }

  //////////////////////
  ////// Sections //////
  //////////////////////

  case class Section(indent: Int, st: Section.Type, elems: List[AST])
      extends Symbol {
    val markerEmptyInNull: String    = st.marker.map(_.toString).getOrElse("")
    val markerNonEmptyInNull: String = st.marker.map(_.toString).getOrElse(" ")
    val newline                      = Text("\n")

    val repr: Repr = {
      var _repr = Repr()

      _repr += (indent match {
        case 0 => markerEmptyInNull
        case 1 => markerNonEmptyInNull
        case _ => makeIndent(indent - 2) + markerNonEmptyInNull + makeIndent(1)
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
    val html: HTML = Seq(
      div(`class` := st.toString)(for (elem <- elems) yield elem.html)
    )

  }

  object Section {
    def apply(indent: Int, st: Type, elem: AST): Section =
      Section(indent, st, elem :: Nil)
    def apply(indent: Int, st: Type, elems: AST*): Section =
      Section(indent, st, elems.to[List])

    final case class Header(elem: AST) extends AST {
      val repr: Repr = elem.repr
      val html: HTML =
        Seq(div(`class` := "Header")(elem.html))
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
        val html: HTML = Seq(code(str), br)
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
    val html: HTML =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
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
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
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
    val html: HTML =
      Seq(div(`class` := name)(name)(details.html))
  }
  object Tag {
    def apply(tp: Type): Tag = Tag(tp, None)
    def apply(tp: Type, details: String): Tag =
      Tag(tp, Option(details))

    trait Type
    case object Deprecated extends Type
    case object Added      extends Type
    case object Removed    extends Type
    case object Modified   extends Type
    case object Upcoming   extends Type
  }

  final case class Tags(indent: Int, elems: List[Tag]) extends AST {
    val repr: Repr =
      elems.map(makeIndent(indent) + _.repr.show()).mkString("\n")
    val html: HTML =
      Seq(
        div(`class` := this.getClass.getSimpleName)(
          for (elem <- elems) yield elem.html
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
      Seq(self.map(div(`class` := "tag_details")(_)).getOrElse("".html))
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
      var _repr = Repr()
      if (tags.exists()) {
        _repr += tags.repr
        if (synopsis.exists() || details.exists()) {
          _repr += "\n"
        }
      }

      if (synopsis.exists()) {
        _repr += synopsis.repr
      }

      if (details.exists()) {
        _repr += "\n"
        _repr += details.repr
      }
      _repr
    }

    val html: HTML = {
      val tagsHtml     = if (tags.exists()) tags.html else "".html
      val synopsisHtml = if (synopsis.exists()) synopsis.html else "".html
      val detailsHtml  = if (details.exists()) details.html else "".html
      Seq(
        div(`class` := this.getClass.getSimpleName)(tagsHtml)(synopsisHtml)(
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
